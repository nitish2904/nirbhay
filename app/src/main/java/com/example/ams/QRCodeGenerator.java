package com.example.ams;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.Ref;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class QRCodeGenerator extends BaseActivity implements View.OnClickListener{
    private ImageView qrCodeImage;
    private Button finishTakingAttendance;
    private final String BASE_URL = "http://192.168.43.99:1234/ams/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrcode_generator);

        qrCodeImage = (ImageView)findViewById(R.id.qrCodeImage);
        finishTakingAttendance = (Button) findViewById(R.id.finishQrCode);

        Intent intent = getIntent();
        byte [] byteArray = intent.getByteArrayExtra("bitmap");
        Bitmap bmp = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        qrCodeImage.setImageBitmap(bmp);
        finishTakingAttendance.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if(view == findViewById(R.id.finishQrCode)){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setMessage("Finish Taking Attendance? This cant be undone.");
            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Toast.makeText(getApplicationContext(), "Attendance Successfully taken", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //if user select "No", just cancel this dialog and continue with app
                    dialog.cancel();
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    //alert to show dialog box: if user clicks on Ok (exits) by pressing back button whole attendance will be lost
    //else finish Taking attendance button is provided
    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage("All attendance will be lost. Do You want to exit?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //delete all the attendance i.e refresh the flush field in the attedance entry of current subject
                RefreshTheFlushField refreshTheFlushField = new RefreshTheFlushField();
                String group_name = getIntent().getStringExtra("group");
                refreshTheFlushField.execute(group_name);
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //if user select "No", just cancel this dialog and continue with app
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private class RefreshTheFlushField extends AsyncTask<String, String , String >{

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            hideProgressDialog();
            showProgressDialog("Updating..please wait..");
        }


        @Override
        protected String doInBackground(String... strings) {
            ///ContentValues params = new ContentValues();

            HashMap<String, Object> params = new HashMap<String, Object>();
            params.put("table_name",  "table_" + strings[0].toLowerCase().trim());

            String link = BASE_URL + "refresh_the_flush_field.php";

            Set set = params.entrySet();
            Iterator iterator = set.iterator();
            try {
                String data = "";
                while (iterator.hasNext()) {
                    Map.Entry mEntry = (Map.Entry) iterator.next();
                    data += URLEncoder.encode(mEntry.getKey().toString(), "UTF-8") + "=" +
                            URLEncoder.encode(mEntry.getValue().toString(), "UTF-8");
                    data += "&";
                }

                if (data != null && data.length() > 0 && data.charAt(data.length() - 1) == '&') {
                    data = data.substring(0, data.length() - 1);
                }
                Log.d("Debug", data);

                URL url=new URL(link);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoInput(true);
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestProperty("Accept","*/*");
                OutputStream out = httpURLConnection.getOutputStream();
                BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(out, "UTF-8"));

                Log.d("data", data);
                bufferedWriter.write(data);
                bufferedWriter.flush();
                bufferedWriter.close();
                InputStream inputStream = new BufferedInputStream(httpURLConnection.getInputStream());


                String result = convertStreamToString(inputStream);
                httpURLConnection.disconnect();
                Log.d("TAG",result);
                //teacherId is defined in sql as primary key
                //so if any user login with the same teacherId, delete this already created user in Firebase


                return result;
            }
            catch(Exception e){
                Log.d("debug",e.getMessage());
                e.printStackTrace();
            }
            return null;
        }

        private String convertStreamToString(InputStream inputStream) {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder("");
            String line;
            try {
                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return sb.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            //if string returned from doinbackground is null, that means Exception occured while connectioon to server
            if(s==null){
                Toast.makeText(QRCodeGenerator.this, "Coudlnt connect to PHPServer", Toast.LENGTH_LONG).show();
            }
            else {

                //otherwise string would contain the JSON returned from php
                JSONParser parser = new JSONParser();
                org.json.simple.JSONObject jsonObject = null;
                try {
                    jsonObject = (org.json.simple.JSONObject) parser.parse(s);
                }catch(ParseException e){
                    e.printStackTrace();
                }
                int successCode = 0;
                if(jsonObject!=null) {
                    Object p = jsonObject.get("success");
                    successCode = Integer.parseInt(p.toString());
                }
                if(jsonObject!=null && successCode==0){
                    Toast.makeText(QRCodeGenerator.this, "Unable to connect to server", Toast.LENGTH_LONG).show();
                }
                else {
                    finish();
                }
            }
        }
    }

}

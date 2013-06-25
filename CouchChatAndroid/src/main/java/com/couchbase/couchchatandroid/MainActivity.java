package com.couchbase.couchchatandroid;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;
import org.ektorp.http.HttpClient;

import java.io.IOException;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            CBLServer server = new CBLServer(getFilesDir().getAbsolutePath());
            HttpClient httpClient = new CBLiteHttpClient(server);
            System.out.println("httpClient: " + httpClient);
            server.allDatabaseNames();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
}

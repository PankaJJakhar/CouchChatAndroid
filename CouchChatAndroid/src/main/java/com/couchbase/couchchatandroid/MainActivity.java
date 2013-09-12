package com.couchbase.couchchatandroid;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.couchbase.cblite.CBLAttachment;
import com.couchbase.cblite.CBLBlobKey;
import com.couchbase.cblite.CBLBlobStore;
import com.couchbase.cblite.CBLBlobStoreWriter;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.auth.CBLFacebookAuthorizer;
import com.couchbase.cblite.auth.CBLPersonaAuthorizer;
import com.couchbase.cblite.cbliteconsole.CBLiteConsoleActivity;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTracker;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTrackerClient;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;
import com.couchbase.cblite.support.CBLMultipartReader;
import com.couchbase.cblite.support.CBLMultipartReaderDelegate;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.ByteArrayBuffer;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.facebook.*;
import com.facebook.model.*;

import android.widget.TextView;
import android.content.Intent;

import junit.framework.Assert;

public class MainActivity extends Activity {

    public static String TAG = "CouchChat";

    private static boolean initializedUrlHandler = false;

    protected WebView mWebView;
    public static final String DATABASE_URL = "http://10.0.2.2:4984";
    public static final String DATABASE_NAME = "couchchat";

    protected static HttpClient httpClient;
    protected CBLServer server = null;
    protected CouchDbInstance dbInstance;
    protected CouchDbConnector couchDbConnector;

    protected final String PERSONA_SIGNIN_URL = "https://login.persona.org/sign_in#NATIVE";
    private static final String PERSONA_GLOBAL_OBJECT_NAME = "__personaAndroid";
    private static final String PERSONA_CALLBACK = "function __personaAndroidCallback(assertion) { " + PERSONA_GLOBAL_OBJECT_NAME + ".onAssertion(assertion); }";

    public enum AuthenticationMechanism {
        PERSONA, FACEBOOK
    }

    private AuthenticationMechanism authenticationMechanism = AuthenticationMechanism.FACEBOOK;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //for some reason a traditional static initializer causes junit to die
        if (!initializedUrlHandler) {
            CBLURLStreamHandlerFactory.registerSelfIgnoreError();
            initializedUrlHandler = true;
        }


        // db is initialized in onPostResume() rather than onCreate()

        final Button buttonFbLogin = (Button) findViewById(R.id.buttonFbLogin);
        buttonFbLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    doFbLogin();
                } catch (Exception e) {
                    alert("Error doing fb login", e);
                }
            }
        });

        final Button buttonPersonaLogin = (Button) findViewById(R.id.buttonPersonaLogin);
        buttonPersonaLogin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setupWebView(getReplicationURL().toExternalForm());   // TODO: this should start an activity rather than doing it this way
            }
        });

        final Button button = (Button) findViewById(R.id.buttonShowConsole);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CBLiteConsoleActivity.class);
                Bundle b = new Bundle();
                b.putString(CBLiteConsoleActivity.INTENT_PARAMETER_DATABASE_NAME, DATABASE_NAME);
                intent.putExtras(b);
                startActivity(intent);
            }
        });


    }


    @Override
    protected void onPause() {
        super.onPause();
        if (httpClient != null) {
            httpClient.shutdown();
        }
        server.close();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();

        startCBLite();
        startDatabase();
        startEktorp();
        // experiment();
        startReplicationsWithUrl(getReplicationURL().toExternalForm());

    }


    private void experiment() {

        String TAG = "CBLTest";
        String localDB = "test";
        String hostDB = "http://nickkitto.iriscouch.com/test";

        ReplicationCommand pullCommand = new ReplicationCommand.Builder()
                .source(hostDB)
                .target(localDB)
                .continuous(false)
                .createTarget(true)
                .build();
        dbInstance.replicate(pullCommand);


    }

    private void doFbLogin() {

        // start Facebook Login
        Session.openActiveSession(this, true, new Session.StatusCallback() {

            // callback when session changes state
            @Override
            public void call(Session session, SessionState state, Exception exception) {

                try {

                    if (exception != null) {
                        throw exception;
                    }

                    if (session.isOpened()) {

                        final String accessToken = session.getAccessToken();

                        // make request to the /me API
                        Request.executeMeRequestAsync(session, new Request.GraphUserCallback() {

                            // callback after Graph API response with user object
                            @Override
                            public void onCompleted(GraphUser user, Response response) {

                                if (user != null) {
                                    TextView welcome = (TextView) findViewById(R.id.hello_world);
                                    welcome.setText("Hello " + user.getName() + "!");


                                    startReplicationsWithFacebookToken(accessToken, (String) user.getProperty("email"));
                                }

                            }
                        });

                    }
                } catch (Exception e) {
                    String message = String.format("Exception accessing facebook session: %s. See logs for details.", e.getLocalizedMessage());
                    alert(message, e);
                }
            }
        });
    }

    private void alert(String message, Exception e) {

        Context context = getApplicationContext();
        CharSequence text = (CharSequence) message;
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();


        Log.e(TAG, message, e);
    }

    protected String getServerPath() {
        String filesDir = getFilesDir().getAbsolutePath();
        return filesDir;
    }

    protected void startCBLite() {
        try {
            server = new CBLServer(getServerPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void startDatabase() {
        CBLDatabase db = server.getExistingDatabaseNamed(DATABASE_NAME);
        db = server.getDatabaseNamed(DATABASE_NAME, true);
        db.open();
    }

    protected void startEktorp() {
        Log.v(TAG, "starting ektorp");

        if (httpClient != null) {
            httpClient.shutdown();
        }

        httpClient = new CBLiteHttpClient(server);
        dbInstance = new StdCouchDbInstance(httpClient);

        EktorpAsyncTask startupTask = new EktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                couchDbConnector = dbInstance.createConnector(DATABASE_NAME, true);
            }

            @Override
            protected void onSuccess() {
                Log.d(TAG, "Ektorp started OK");

            }
        };
        startupTask.execute();

    }

    protected URL getReplicationURL() {
        try {
            return new URL(String.format("%s/%s", DATABASE_URL, DATABASE_NAME));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void startReplicationsWithPersonaAssertions(String assertion) {

        Log.d(TAG, "startReplicationsWithPersonaAssertions()");

        httpClient = new CBLiteHttpClient(server);
        dbInstance = new StdCouchDbInstance(httpClient);

        // create a local database
        couchDbConnector = dbInstance.createConnector(DATABASE_NAME, true);

        // add on the persona assertion as a parameter.  needed for hack
        // described here: http://bit.ly/17lsw2a
        ReplicationCommand pushCommand = new ReplicationCommand.Builder()
                .source(getReplicationURL().toExternalForm() + "?" + CBLPersonaAuthorizer.QUERY_PARAMETER + "=" + assertion)
                .target(DATABASE_NAME)
                .continuous(false)
                .build();

        ReplicationStatus status = dbInstance.replicate(pushCommand);
        Log.d(TAG, "replicationStatus: " + status);

    }

    public void startReplicationsWithFacebookToken(String accessToken, String email) {

        Log.d(TAG, "startReplicationsWithFacebookToken()");

        httpClient = new CBLiteHttpClient(server);
        dbInstance = new StdCouchDbInstance(httpClient);

        // create a local database
        couchDbConnector = dbInstance.createConnector(DATABASE_NAME, true);

        String urlWithExtraParams = null;
        try {
            String emailEncoded = URLEncoder.encode(email, "utf-8");
            urlWithExtraParams = String.format("%s?%s=%s&%s=%s",
                    getReplicationURL().toExternalForm(),
                    CBLFacebookAuthorizer.QUERY_PARAMETER,
                    accessToken,
                    CBLFacebookAuthorizer.QUERY_PARAMETER_EMAIL,
                    emailEncoded
            );
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }

        startReplicationsWithUrl(urlWithExtraParams);


    }

    public void startReplicationsWithUrl(String urlWithExtraParams) {

        final ReplicationCommand pushReplicationCommand = new ReplicationCommand.Builder()
                .source(DATABASE_NAME)
                .target(urlWithExtraParams)
                .continuous(true)
                .build();

        EktorpAsyncTask pushReplication = new EktorpAsyncTask() {
            @Override
            protected void doInBackground() {
                dbInstance.replicate(pushReplicationCommand);
            }
        };

        pushReplication.execute();

        final ReplicationCommand pullReplicationCommand = new ReplicationCommand.Builder()
                .source(urlWithExtraParams)
                .target(DATABASE_NAME)
                .continuous(true)
                .build();

        EktorpAsyncTask pullReplication = new EktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                dbInstance.replicate(pullReplicationCommand);
            }
        };

        pullReplication.execute();

    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.loadUrl(PERSONA_SIGNIN_URL);
        }
    }


    protected void setupWebView(final String personaUrl) {


        // Let's display the progress in the activity title bar, like the
        // browser app does.
        getWindow().requestFeature(Window.FEATURE_PROGRESS);
        mWebView = new WebView(this);
        setContentView(mWebView);

        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        mWebView.addJavascriptInterface(new BrowserIDInterface(), PERSONA_GLOBAL_OBJECT_NAME);

        final Activity activity = this;
        mWebView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                // Activities and WebViews measure progress with different scales.
                // The progress meter will automatically disappear when we reach 100%
                activity.setProgress(progress * 1000);
            }
        });
        mWebView.setWebViewClient(new WebViewClient() {
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.d(TAG, "setupWebview.onReceivedError() called: " + description + " url: " + failingUrl);
                Toast.makeText(activity, "Oh no! " + description, Toast.LENGTH_SHORT).show();
            }

            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "setupWebview.onPageFinished() called with url: " + url);
                if (url.equals(PERSONA_SIGNIN_URL)) {
                    Log.d("LoginActivity", PERSONA_GLOBAL_OBJECT_NAME);

                    String cmd = "javascript:BrowserID.internal.get('" + personaUrl + "', " + PERSONA_CALLBACK + ");";
                    Log.d("LoginActivity", cmd);
                    mWebView.loadUrl(cmd);
                }
            }
        });
    }

    private class BrowserIDInterface {
        public void onAssertion(String assertion) {
            Log.d("BrowserIDInterface", "we got an assertion!");
            Log.v("BrowserIDInterface", assertion);

            Context context = getApplicationContext();
            CharSequence text = "We got an assertion!";
            int duration = Toast.LENGTH_LONG;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();

            startReplicationsWithPersonaAssertions(assertion);

        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Session.getActiveSession().onActivityResult(this, requestCode, resultCode, data);
    }

}

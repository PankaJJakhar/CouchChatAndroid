package com.couchbase.couchchatandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Activity;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.auth.CBLPersonaAuthorizer;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;
import com.couchbase.cblite.replicator.CBLPusher;
import com.couchbase.cblite.replicator.CBLReplicator;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;
import com.couchbase.cblite.support.FileDirUtils;

import junit.framework.Assert;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.ReplicationStatus;
import org.ektorp.ViewQuery;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class MainActivity extends Activity {

    public static String TAG = "CouchChat";

    private static boolean initializedUrlHandler = false;

    protected WebView mWebView;
    public static final String DATABASE_URL = "http://10.0.2.2:4984";
    public static final String DATABASE_NAME = "cblite-test";

    protected final String SIGNIN_URL = "https://login.persona.org/sign_in#NATIVE";

    protected static HttpClient httpClient;
    protected CBLServer server = null;
    protected CBLDatabase database = null;
    protected CouchDbInstance dbInstance;
    protected CouchDbConnector couchDbConnector;

    // This is the name our JS interface becomes on window
    private static final String GLOBAL_OBJECT_NAME = "__personaAndroid";
    private static final String CALLBACK = "function __personaAndroidCallback(assertion) { " + GLOBAL_OBJECT_NAME + ".onAssertion(assertion); }";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //for some reason a traditional static initializer causes junit to die
        if(!initializedUrlHandler) {
            CBLURLStreamHandlerFactory.registerSelfIgnoreError();
            initializedUrlHandler = true;
        }

        deleteExistingLocalDB();
        startCBLite();
        startDatabase();
        startEktorp();
        // setupWebView(getReplicationURL().toExternalForm());
        doScratchpad();


    }

    protected void doScratchpad() {

        EktorpAsyncTask asyncTask = new EktorpAsyncTask() {

            @Override
            protected void doInBackground() {
                try {
                    testPuller();
                } catch (Throwable throwable) {
                    Log.e(TAG, throwable.getLocalizedMessage(), throwable);
                }
            }

            @Override
            protected void onSuccess() {
                Log.d(TAG, "onSucces()");
            }
        };
        asyncTask.execute();

    }

    public void testPusher() throws Throwable {

        URL remote = getReplicationURL();

        // deleteRemoteDB(remote);

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        CBLBody body = new CBLBody(documentProperties);
        CBLRevision rev1 = new CBLRevision(body);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        CBLRevision rev2 = database.putRevision(new CBLRevision(documentProperties), rev1.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc2");
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new CBLRevision(documentProperties), null, false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        final CBLReplicator repl = database.getReplicator(remote, true, false, server.getWorkExecutor());
        ((CBLPusher)repl).setCreateTarget(true);
        repl.start();

        while(repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals("3", repl.getLastSequence());
    }

    public void testPuller() throws Throwable {

        //force a push first, to ensure that we have data to pull
        testPusher();

        URL remote = getReplicationURL();

        final CBLReplicator repl = database.getReplicator(remote, false, false, server.getWorkExecutor());
        repl.start();

        while(repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        String lastSequence = repl.getLastSequence();
        Assert.assertTrue("2".equals(lastSequence) || "3".equals(lastSequence));
        Assert.assertEquals(2, database.getDocumentCount());


        //wait for a short time here
        //we want to ensure that the previous replicator has really finished
        //writing its local state to the server
        Thread.sleep(2*1000);

        final CBLReplicator repl2 = database.getReplicator(remote, false, false, server.getWorkExecutor());
        repl2.start();

        while(repl2.isRunning()) {
            Log.i(TAG, "Waiting for replicator2 to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals(3, database.getLastSequence());

        CBLRevision doc = database.getDocumentWithIDAndRev("doc1", null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("2-"));
        Assert.assertEquals(1, doc.getProperties().get("foo"));

        doc = database.getDocumentWithIDAndRev("doc2", null, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("1-"));
        Assert.assertEquals(true, doc.getProperties().get("fnord"));

    }

    protected void deleteRemoteDB(URL url) {
        try {
            Log.v(TAG, String.format("Deleting %s", url.toExternalForm()));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            String userInfo = url.getUserInfo();
            if(userInfo != null) {
                byte[] authEncBytes = Base64.encode(userInfo.getBytes(), Base64.DEFAULT);

                conn.setRequestProperty("Authorization", "Basic " + new String(authEncBytes));
            }

            conn.setRequestMethod("DELETE");
            conn.connect();
            int responseCode = conn.getResponseCode();
            Assert.assertTrue(responseCode < 300 || responseCode == 404);
        } catch (Exception e) {
            Log.e(TAG, "Exceptiong deleting remote db", e);
        }
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

    private void deleteExistingLocalDB() {
        String serverPath = getServerPath();
        File serverPathFile = new File(serverPath);
        FileDirUtils.deleteRecursive(serverPathFile);
        serverPathFile.mkdir();
    }

    protected void startDatabase() {
        database = server.getDatabaseNamed(DATABASE_NAME, true);
        database.open();
    }

    protected void startEktorp() {
        Log.v(TAG, "starting ektorp");

        if(httpClient != null) {
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

    public void startReplications(String assertion) {

        Log.d(TAG, "startReplications()");

        CountDownLatch doneSignal = new CountDownLatch(1);

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

        try {
            doneSignal.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.loadUrl(SIGNIN_URL);
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

        mWebView.addJavascriptInterface(new BrowserIDInterface(), GLOBAL_OBJECT_NAME);

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
                if (url.equals(SIGNIN_URL)) {
                    Log.d("LoginActivity", GLOBAL_OBJECT_NAME);

                    String cmd = "javascript:BrowserID.internal.get('" + personaUrl + "', " + CALLBACK + ");";
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

            startReplications(assertion);


        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
}

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
import com.couchbase.cblite.support.Base64;
import com.couchbase.cblite.support.CBLMultipartReader;
import com.couchbase.cblite.support.CBLMultipartReaderDelegate;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
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
        // experiment2();
        //experiment3();
        //experiment4();
        // experiment5();
        experiment6();

    }

    public void experiment6() {

        URL testURL = getReplicationURL();
        final MockHttpClient mockHttpClient = new MockHttpClient();

        CBLChangeTrackerClient client = new CBLChangeTrackerClient() {

            @Override
            public void changeTrackerStopped(CBLChangeTracker tracker) {
                Log.v(TAG, "changeTrackerStopped");
            }

            @Override
            public void changeTrackerReceivedChange(Map<String, Object> change) {
                Object seq = change.get("seq");
                Log.v(TAG, "changeTrackerReceivedChange: " + seq.toString());
            }

            @Override
            public org.apache.http.client.HttpClient getHttpClient() {
                return mockHttpClient;
            }
        };

        final CBLChangeTracker changeTracker = new CBLChangeTracker(testURL, CBLChangeTracker.TDChangeTrackerMode.Continuous, 0, client, null);

        AsyncTask task = new AsyncTask<Object, Object, Object>() {
            @Override
            protected Object doInBackground(Object... aParams) {
                changeTracker.start();
                return null;
            }
        };
        task.execute();

        try {

            // expected behavior:
            // when:
            //    mockHttpClient throws IOExceptions -> it should start high and then back off and numTimesExecute should be low

            for (int i=0; i<30; i++) {

                int numTimesExectutedAfter10seconds = 0;

                try {
                    Thread.sleep(1000);

                    // take a snapshot of num times the http client was called after 10 seconds
                    if (i == 10) {
                        numTimesExectutedAfter10seconds = mockHttpClient.getNumTimesExecuteCalled();
                    }

                    // take another snapshot after 20 seconds have passed
                    if (i == 20) {
                        // by now it should have backed off, so the delta between 10s and 20s should be small
                        int delta = mockHttpClient.getNumTimesExecuteCalled() - numTimesExectutedAfter10seconds;
                        Assert.assertTrue(delta < 25);
                    }


                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            changeTracker.stop();
        }

    }


    class MockHttpClient implements org.apache.http.client.HttpClient {

        private int numTimesExecuteCalled = 0;

        public int getNumTimesExecuteCalled() {
            return numTimesExecuteCalled;
        }

        @Override
        public HttpParams getParams() {
            return null;
        }

        @Override
        public ClientConnectionManager getConnectionManager() {
            return null;
        }

        @Override
        public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException, ClientProtocolException {
            numTimesExecuteCalled++;
            throw new IOException("Test IOException");
        }

        @Override
        public HttpResponse execute(HttpUriRequest httpUriRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
            numTimesExecuteCalled++;
            throw new IOException("Test IOException");
        }

        @Override
        public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest) throws IOException, ClientProtocolException {
            numTimesExecuteCalled++;
            throw new IOException("Test IOException");
        }

        @Override
        public HttpResponse execute(HttpHost httpHost, HttpRequest httpRequest, HttpContext httpContext) throws IOException, ClientProtocolException {
            numTimesExecuteCalled++;
            throw new IOException("Test IOException");
        }

        @Override
        public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
            throw new IOException("<T> Test IOException");
        }

        @Override
        public <T> T execute(HttpUriRequest httpUriRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
            throw new IOException("<T> Test IOException");
        }

        @Override
        public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler) throws IOException, ClientProtocolException {
            throw new IOException("<T> Test IOException");
        }

        @Override
        public <T> T execute(HttpHost httpHost, HttpRequest httpRequest, ResponseHandler<? extends T> responseHandler, HttpContext httpContext) throws IOException, ClientProtocolException {
            throw new IOException("<T> Test IOException");
        }


    }

    public void experiment5() throws IOException {

        CBLDatabase database = server.getDatabaseNamed(DATABASE_NAME);

        CBLBlobStore attachments = database.getAttachments();
        attachments.deleteBlobs();
        Assert.assertEquals(0, attachments.count());

        CBLStatus status = new CBLStatus();
        Map<String, Object> rev1Properties = new HashMap<String, Object>();
        rev1Properties.put("foo", 1);
        rev1Properties.put("bar", false);
        CBLRevision rev1 = database.putRevision(new CBLRevision(rev1Properties, database), null, false, status);

        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        StringBuffer largeAttachment = new StringBuffer();
        for (int i = 0; i < CBLDatabase.kBigAttachmentLength; i++) {
            largeAttachment.append("big attachment!");
        }
        byte[] attach1 = largeAttachment.toString().getBytes();
        status = database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach1), rev1.getSequence(), "attach", "text/plain", rev1.getGeneration());
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        CBLAttachment attachment = database.getAttachmentForSequence(rev1.getSequence(), "attach", status);
        Assert.assertEquals(CBLStatus.OK, status.getCode());
        Assert.assertEquals("text/plain", attachment.getContentType());
        byte[] data = IOUtils.toByteArray(attachment.getContentStream());
        Assert.assertTrue(Arrays.equals(attach1, data));

        EnumSet<CBLDatabase.TDContentOptions> contentOptions = EnumSet.of(
                CBLDatabase.TDContentOptions.TDIncludeAttachments,
                CBLDatabase.TDContentOptions.TDBigAttachmentsFollow
        );

        Map<String, Object> attachmentDictForSequence = database.getAttachmentsDictForSequenceWithContent(
                rev1.getSequence(),
                contentOptions
        );

        Map<String, Object> innerDict = (Map<String, Object>) attachmentDictForSequence.get("attach");

        if (!innerDict.containsKey("stub")) {
            throw new RuntimeException("Expected attachment dict to have 'stub' key");
        }

        if (((Boolean) innerDict.get("stub")).booleanValue() == false) {
            throw new RuntimeException("Expected attachment dict 'stub' key to be true");
        }

        if (!innerDict.containsKey("follows")) {
            throw new RuntimeException("Expected attachment dict to have 'follows' key");
        }

        CBLRevision rev1WithAttachments = database.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), contentOptions);
        Map<String, Object> rev1PropertiesPrime = rev1WithAttachments.getProperties();
        rev1PropertiesPrime.put("foo", 2);
        CBLRevision rev2 = database.putRevision(rev1WithAttachments, rev1WithAttachments.getRevId(), false, status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

    }

    class MultipartReaderTest {

        class TestMultipartReaderDelegate implements CBLMultipartReaderDelegate {

            private ByteArrayBuffer currentPartData;
            private List<Map<String, String>> headersList;
            private List<ByteArrayBuffer> partList;

            public void startedPart(Map<String, String> headers) {
                Assert.assertNull(currentPartData);
                if (partList == null) {
                    partList = new ArrayList<ByteArrayBuffer>();
                }
                currentPartData = new ByteArrayBuffer(1024);
                partList.add(currentPartData);
                if (headersList == null) {
                    headersList = new ArrayList<Map<String, String>>();
                }
                headersList.add(headers);
            }

            public void appendToPart(byte[] data) {
                Assert.assertNotNull(currentPartData);
                currentPartData.append(data, 0, data.length);
            }

            public void finishedPart() {
                Assert.assertNotNull(currentPartData);
                currentPartData = null;
            }

        }

        public void testParseContentType() {

            Charset utf8 = Charset.forName("UTF-8");
            HashMap<String, byte[]> contentTypes = new HashMap<String, byte[]>();
            contentTypes.put("multipart/related; boundary=\"BOUNDARY\"", new String("\r\n--BOUNDARY").getBytes(utf8));
            contentTypes.put("multipart/related; boundary=BOUNDARY", new String("\r\n--BOUNDARY").getBytes(utf8));
            contentTypes.put("multipart/related;boundary=X", new String("\r\n--X").getBytes(utf8));

            for (String contentType : contentTypes.keySet()) {
                CBLMultipartReaderDelegate delegate = null;
                CBLMultipartReader reader = new CBLMultipartReader(contentType, delegate);
                byte[] expectedBoundary = (byte[]) contentTypes.get(contentType);
                byte[] boundary = reader.getBoundary();
                Assert.assertTrue(Arrays.equals(boundary, expectedBoundary));
            }

            try {
                CBLMultipartReaderDelegate delegate = null;
                CBLMultipartReader reader = new CBLMultipartReader("multipart/related; boundary=\"BOUNDARY", delegate);
                Assert.assertTrue("Should not have gotten here, above lines should have thrown exception", false);
            } catch (Exception e) {
                // expected exception
            }

        }

        public void testParseHeaders() {
            String testString = new String("\r\nFoo: Bar\r\n Header : Val ue ");
            CBLMultipartReader reader = new CBLMultipartReader("multipart/related;boundary=X", null);
            reader.parseHeaders(testString);
            Assert.assertEquals(reader.headers.keySet().size(), 2);
        }

        public void testReaderOperation() {

            Charset utf8 = Charset.forName("UTF-8");

            byte[] mime = new String("--BOUNDARY\r\nFoo: Bar\r\n Header : Val ue \r\n\r\npart the first\r\n--BOUNDARY  \r\n\r\n2nd part\r\n--BOUNDARY--").getBytes(utf8);

            for (int chunkSize = 1; chunkSize <= mime.length; ++chunkSize) {
                ByteArrayInputStream mimeInputStream = new ByteArrayInputStream(mime);
                TestMultipartReaderDelegate delegate = new TestMultipartReaderDelegate();
                String contentType = "multipart/related; boundary=\"BOUNDARY\"";
                CBLMultipartReader reader = new CBLMultipartReader(contentType, delegate);
                Assert.assertFalse(reader.finished());

                int location = 0;
                int length = 0;

                do {
                    Assert.assertTrue("Parser didn't stop at end", location < mime.length);
                    length = Math.min(chunkSize, (mime.length - location));
                    byte[] bytesRead = new byte[length];
                    mimeInputStream.read(bytesRead, 0, length);
                    reader.appendData(bytesRead);
                    location += chunkSize;
                } while (!reader.finished());

                Assert.assertEquals(delegate.partList.size(), 2);
                Assert.assertEquals(delegate.headersList.size(), 2);

                byte[] part1Expected = new String("part the first").getBytes(utf8);
                byte[] part2Expected = new String("2nd part").getBytes(utf8);
                ByteArrayBuffer part1 = delegate.partList.get(0);
                ByteArrayBuffer part2 = delegate.partList.get(1);
                Assert.assertTrue(Arrays.equals(part1.toByteArray(), part1Expected));
                Assert.assertTrue(Arrays.equals(part2.toByteArray(), part2Expected));

                Map<String, String> headers1 = delegate.headersList.get(0);
                Assert.assertTrue(headers1.containsKey("Foo"));
                Assert.assertEquals(headers1.get("Foo"), "Bar");

                Assert.assertTrue(headers1.containsKey("Header"));
                Assert.assertEquals(headers1.get("Header"), "Val ue");

            }


        }


    }

    private void experiment3() {

        MultipartReaderTest readerTest = new MultipartReaderTest();
        readerTest.testParseContentType();
        readerTest.testParseHeaders();
        readerTest.testReaderOperation();
        System.out.println("done");


    }

    public void experiment4() {


        CBLDatabase database = server.getDatabaseNamed(DATABASE_NAME);

        CBLBlobStore attachments = database.getAttachments();

        CBLBlobStoreWriter blobWriter = new CBLBlobStoreWriter(attachments);
        String testBlob = "foo";
        blobWriter.appendData(new String(testBlob).getBytes());
        blobWriter.finish();

        String sha1Base64Digest = "sha1-C+7Hteo/D9vJXQ3UfzxbwnXaijM=";
        Assert.assertEquals(blobWriter.sHA1DigestString(), sha1Base64Digest);
        Assert.assertEquals(blobWriter.mD5DigestString(), "md5-rL0Y20zC+Fzt72VPzMSk2A==");

        // install it
        blobWriter.install();

        // look it up in blob store and make sure it's there
        CBLBlobKey blobKey = new CBLBlobKey(sha1Base64Digest);
        byte[] blob = attachments.blobForKey(blobKey);
        Assert.assertTrue(Arrays.equals(testBlob.getBytes(Charset.forName("UTF-8")), blob));

        System.out.println("");


    }


    private void experiment2() {

        EktorpAsyncTask asyncTask = new EktorpAsyncTask() {
            @Override
            protected void doInBackground() {

                try {

                    /*

                    NSDictionary* attachments = rev[@"_attachments"];
    for (NSString* attachmentName in [CBLCanonicalJSON orderedKeys: attachments]) {
        NSDictionary* attachment = attachments[attachmentName];
        if (attachment[@"follows"]) {
            if (!bodyStream) {
                // Create the HTTP multipart stream:
                bodyStream = [[CBLMultipartWriter alloc] initWithContentType: @"multipart/related"
                                                                      boundary: nil];
                [bodyStream setNextPartsHeaders: $dict({@"Content-Type", @"application/json"})];
                // Use canonical JSON encoder so that _attachments keys will be written in the
                // same order that this for loop is processing the attachments.
                NSData* json = [CBLCanonicalJSON canonicalData: rev.properties];
                [bodyStream addData: json];
            }
            NSString* disposition = $sprintf(@"attachment; filename=%@", CBLQuoteString(attachmentName));
            NSString* contentType = attachment[@"type"];
            NSString* contentEncoding = attachment[@"encoding"];
            [bodyStream setNextPartsHeaders: $dict({@"Content-Disposition", disposition},
                                                   {@"Content-Type", contentType},
                                                   {@"Content-Encoding", contentEncoding})];
            [bodyStream addFileURL: [_db fileForAttachmentDict: attachment]];
        }
    }


                     */

                    org.apache.http.client.HttpClient httpClient = new DefaultHttpClient();

                    HttpPost post = new HttpPost(getReplicationURL().toExternalForm());
                    MultipartEntity multiPart = new MultipartEntity();


                    Map<String, Object> revProperties = new HashMap<String, Object>();
                    revProperties.put("foo", 1);
                    revProperties.put("bar", false);
                    String json = CBLServer.getObjectMapper().writeValueAsString(revProperties);

                    Charset utf8charset = Charset.forName("UTF-8");

                    multiPart.addPart("param1", new StringBody(json, "application/json", utf8charset));

                    String body = new String("hello");
                    byte[] bytes = body.getBytes();
                    multiPart.addPart("file", new ByteArrayBody(bytes, "application/png", "whatever.png"));

                    post.setEntity(multiPart);
                    httpClient.execute(post);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        };
        asyncTask.execute();

    }

    private void experiment() {

        try {
            CBLDatabase database = server.getDatabaseNamed(DATABASE_NAME);
            CBLStatus status = new CBLStatus();
            Map<String, Object> rev1Properties = new HashMap<String, Object>();
            rev1Properties.put("foo", 1);
            rev1Properties.put("bar", false);
            CBLRevision rev1 = database.putRevision(new CBLRevision(rev1Properties, database), null, false, status);

            Assert.assertEquals(CBLStatus.CREATED, status.getCode());

            StringBuffer largeAttachment = new StringBuffer();
            for (int i = 0; i < CBLDatabase.kBigAttachmentLength; i++) {
                largeAttachment.append("big attachment!");
            }
            byte[] attach1 = largeAttachment.toString().getBytes();
            status = database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach1), rev1.getSequence(), "attach", "text/plain", rev1.getGeneration());
            Assert.assertEquals(CBLStatus.CREATED, status.getCode());

            CBLAttachment attachment = database.getAttachmentForSequence(rev1.getSequence(), "attach", status);
            Assert.assertEquals(CBLStatus.OK, status.getCode());
            Assert.assertEquals("text/plain", attachment.getContentType());
            byte[] data = IOUtils.toByteArray(attachment.getContentStream());
            Assert.assertTrue(Arrays.equals(attach1, data));

            EnumSet<CBLDatabase.TDContentOptions> contentOptions = EnumSet.of(
                    CBLDatabase.TDContentOptions.TDIncludeAttachments,
                    CBLDatabase.TDContentOptions.TDBigAttachmentsFollow
            );

            Map<String, Object> attachmentDictForSequence = database.getAttachmentsDictForSequenceWithContent(
                    rev1.getSequence(),
                    contentOptions
            );

            Map<String, Object> innerDict = (Map<String, Object>) attachmentDictForSequence.get("attach");

            if (!innerDict.containsKey("stub")) {
                throw new RuntimeException("Expected attachment dict to have 'stub' key");
            }

            if (((Boolean) innerDict.get("stub")).booleanValue() == false) {
                throw new RuntimeException("Expected attachment dict 'stub' key to be true");
            }

            if (!innerDict.containsKey("follows")) {
                throw new RuntimeException("Expected attachment dict to have 'follows' key");
            }


            Log.d(TAG, "attachmentDictForSequence: " + attachmentDictForSequence);

        } catch (IOException e) {

            e.printStackTrace();
        }

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

        /*
        ReplicationCommand pushCommand = new ReplicationCommand.Builder()
                .source(urlWithExtraParams)
                .target(DATABASE_NAME)
                .continuous(false)
                .build();

        ReplicationStatus status = dbInstance.replicate(pushCommand); */


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

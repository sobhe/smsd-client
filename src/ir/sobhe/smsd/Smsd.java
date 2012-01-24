package ir.sobhe.smsd;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class Smsd extends Activity {
	private TextView tv;
	Handler handel;
	Runnable fetcherRunnable = new FetcherRunnable();
	Runnable senderRunnable = new SenderRunnable();
	Thread fetcher;
	Thread sender;
	private MessagesDataSource datasource;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(R.layout.main);
        tv = (TextView) findViewById(R.id.tv);
        handel = new Handler();
        fetcher = new Thread(fetcherRunnable);
        sender = new Thread(senderRunnable);
        datasource = new MessagesDataSource(this);
    }
    
    @Override
    public void onResume(){
    	super.onResume();
    	datasource.open();
    	fetcher.start();
    	sender.start();
    }
    
    @Override
    public void onPause(){
    	super.onPause();
    	datasource.close();
    }
    
    @Override
    public void onDestroy(){
    	super.onDestroy();
    }
    
    class FetcherRunnable implements Runnable{

		@Override
		public void run() {
			int i = 0;
			while(true){
				i++;
				if (i == 1) {
					handel.post(new Runnable() {
						@Override
						public void run() {
							tv.append("fetcher started\n");
						}
					});
				}
				
				if(i % 9 == 2){
					final int l = datasource.getAllMessages().size();
					handel.post(new Runnable() {
						@Override
						public void run() {
							tv.append("number of messages in databse:" + l + " \n");
						}
					});
				}
				if(i >= 200)
				{
					String text = tv.getText().toString();
					text = text.substring(text.indexOf('\n', text.length()));
					tv.setText(text);
					i = 50;
				}
				try{
					HttpClient httpclient = new DefaultHttpClient();
			    	HttpPost httppost = new HttpPost(Constants.fetch_url);
					List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
					nameValuePairs.add(new BasicNameValuePair("name", "htc-tatto"));
					nameValuePairs.add(new BasicNameValuePair("key", "2f1a5ee55fe8435b6aa82782d318f5e2"));
					httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
					HttpResponse response = httpclient.execute(httppost);
					
					InputStream reponseStream = response.getEntity().getContent();
					Scanner in = new Scanner(reponseStream);
					
					String tempString = "";
					
					while(in.hasNext()){
						tempString += in.nextLine();
					}
					
					final String responseString = tempString;
					handel.post(new Runnable() {
						
						@Override
						public void run() {
							tv.append(responseString + "\n");
						}
					});
					
					JSONObject json = (JSONObject) new JSONTokener(responseString).nextValue();
					JSONArray messages = json.getJSONArray("messages");
					
					if (messages.length() > 0) {
						for(int j = 0; j < messages.length(); j++){
							JSONObject m_json = messages.getJSONObject(j);
							datasource.createMessage(m_json.getString("to"), m_json.getString("text"));
						}
						final int l = messages.length();
						handel.post(new Runnable() {
							
							@Override
							public void run() {
								tv.append("Added " + l + " message to db!\n");
							}
						});
					}
				}
				catch(ClientProtocolException e){
					e.printStackTrace();
				}
				catch(IOException e){
					e.printStackTrace();
				} catch (JSONException e) {
					e.printStackTrace();
				}
				
				
				try {
					Thread.sleep(Constants.fetch_interval);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
    }
    
    class SenderRunnable implements Runnable{

		@Override
		public void run() {
			int i = 0;
			while(true){
				i++;
				if (i == 1) {
					handel.post(new Runnable() {
						@Override
						public void run() {
							tv.append("sender started\n");
						}
					});
				}
				
				List<Message> messages = datasource.getAllMessages();
				
				if(messages.size() > 0) {
					Message toSend = messages.get(0);
					sendSMS(toSend.getTo(), toSend.getMessage());
					datasource.deleteMessage(toSend);
				}
				
				try {
					Thread.sleep(Constants.sender_interval);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
    }
    
    private void sendSMS(String to, String text) {
    	
    	String SENT = "SMS_SENT";
        String DELIVERED = "SMS_DELIVERED";
        
        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(DELIVERED), 0);
        
        registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context arg0, Intent arg1) {
            	final String s;
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        s = "SMS sent";
                        handelIntentBroadcast(s);
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        s = "Generic failure";
                        handelIntentBroadcast(s);
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        s = "No service";
                        handelIntentBroadcast(s);
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        s = "Null PDU";
                        handelIntentBroadcast(s);
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        s = "Radio off";
                        handelIntentBroadcast(s);
                        break;
                }
            }
        }, new IntentFilter(SENT));
        
        //---when the SMS has been delivered---
        registerReceiver(new BroadcastReceiver(){
            @Override
            public void onReceive(Context arg0, Intent arg1) {
            	final String s;
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        s = "SMS delivered";
                        handelIntentBroadcast(s);
                        break;
                    case Activity.RESULT_CANCELED:
                        s = "SMS not delivered";
                        handelIntentBroadcast(s);
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));
    	
		SmsManager sms = SmsManager.getDefault();
		ArrayList<String> messages = sms.divideMessage(text);
		
		//number of messages
		int nom = messages.size();
		ArrayList<PendingIntent> sendIntents = new ArrayList<PendingIntent>(nom);
		ArrayList<PendingIntent> deliverIntents = new ArrayList<PendingIntent>(nom);
		
		for (int i = 0; i < nom; i++) {
			sendIntents.add(sentPI);
			deliverIntents.add(deliveredPI);
		}
		sms.sendMultipartTextMessage(to, null, messages, sendIntents, deliverIntents);
	}
    
    public void handelIntentBroadcast(final String s) {
    	Log.w("SMTHNG", s);
    	handel.post(new Runnable() {
            
            @Override
            public void run() {
                tv.append(s + "\n");
            }
        });
    }
}
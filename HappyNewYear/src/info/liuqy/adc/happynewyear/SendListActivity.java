package info.liuqy.adc.happynewyear;

import info.liuqy.adc.happynewyear.HappyNewYearActivity.Language;
import info.liuqy.adc.happynewyear.HappyNewYearActivity.Market;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.ListActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.SimpleAdapter;
import android.widget.Toast;

public class SendListActivity extends ListActivity {

	static final String KEY_TO = "TO";
	static final String KEY_SMS = "SMS";
	static final String INDEX_KEY = "index_key";
	static final String SENT_ACTION = "SMS_SENT_ACTION";
	static final String DELIVERED_ACTION = "SMS_DELIVERED_ACTION";
	static final String EXTRA_IDX = "contact_adapter_idx";
	static final String EXTRA_TONUMBER = "sms_to_number";
	static final String EXTRA_SMS = "sms_content";
	static final String FIELD_SEND_STATE = "status";
	static final String SMS_INDEX_ACTION = "sms_index_action";
	private static final String SEDNSUCCESSED = "Send successed!";
	private static final String DB_NAME = "data";
	private static final String TBL_NAME = "sms";
	static final String FIELD_TO = "to_number";
	static final String FIELD_SMS = "sms";
	static final String KEY_ROWID = "_id";
	
	private static final int HAPPYNEWYEAR_ID = 1;
	protected static final int SMSLIST_SEND_START = 0x101;
	protected static final int SMSLIST_SEND_FINISH = 0x102;
	protected static final int SMS_SEND_START = 0x103;
	protected static final int SMS_SEND_SUCCESSED = 0x104;
	protected static final int SMS_SEND_DELIVERED = 0x106;
	protected static final int NOTIFICATION_ID = 0x110;
	private static final int DB_VERSION = 2;
	
	private NotificationManager mNotificationManager;
	private Notification mNotification;
	private int maxSms = 0;
	private int deliveredSms = 0;
	private boolean send_state = false;
	
	Bundle sendlist = new Bundle();
	ListView smsListView = null;
	// [<TO, number>,<SMS, sms>]
	List<Map<String, String>> smslist = new LinkedList<Map<String, String>>();
	SimpleAdapter adapter;

	static BroadcastReceiver smsSentReceiver = null;
	static BroadcastReceiver smsDeliveredReceiver = null;
	static BroadcastReceiver smsIndexReceiver = null;

	SQLiteOpenHelper dbHelper = null;
	SQLiteDatabase db = null;

	// 判断是否发送过的消息处理
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			int idx = msg.what;
			smsListView.getChildAt(idx).setBackgroundColor(
					getResources().getColor(R.color.gray));
			super.handleMessage(msg);
		}
	};
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sendlist);

		smsListView = getListView();
		handleIntent();
		initdb();
		createReceivers();

		adapter = new SimpleAdapter(this, smslist,
				android.R.layout.simple_list_item_2, new String[] { KEY_TO,
						KEY_SMS }, new int[] { android.R.id.text1,
						android.R.id.text2 });
		this.setListAdapter(adapter);
	}

	public void handleIntent() {
		Bundle data = this.getIntent().getExtras();
		if (data != null) {
			// data.getParcelable(HappyNewYearActivity.SENDLIST);
			String mark = data.getString("mark");
			String lang = data.getString("lang");
			readContacts(mark, lang);
		}
	}

	public void sendSms(View v) {
		// 发送消息线程非UI线程
		new Thread(new sendSmsThread(this)).start();
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Question for you: where is the right place to register receivers?
		registerReceivers();
	}

	@Override
	protected void onStop() {
		super.onStop();
		// Question for you: where is the right place to unregister receivers?
		unregisterReceivers();
	}

	// 处理消息发送广播，设置颜色，修改本地数据库
	protected void createReceivers() {
		if (smsSentReceiver == null)
			smsSentReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					int idx = intent.getIntExtra(EXTRA_IDX, -1);
					Log.i("idx", String.valueOf(idx));
					String toNum = intent.getStringExtra(EXTRA_TONUMBER);
					String sms = intent.getStringExtra(EXTRA_SMS);
					int succ = getResultCode();
					if (succ == Activity.RESULT_OK) {
						smsListView.getChildAt(idx).setBackgroundColor(
								getResources().getColor(R.color.yellow));
						Toast.makeText(SendListActivity.this,
								"Sent to " + toNum + " OK!", Toast.LENGTH_SHORT)
								.show();
						deliveredSms++;
						notifySuccessfulDelivery("Delivered to " + toNum
								+ " OK!", sms);
						if (send_state) {
							updateDataBase(toNum, sms, SEDNSUCCESSED);
						} else {
							saveToDatabase(toNum, sms);
						}
					} else {
						smsListView.getChildAt(idx).setBackgroundColor(
								getResources().getColor(R.color.red));
					}
				}
			};

		if (smsDeliveredReceiver == null)
			smsDeliveredReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					int idx = intent.getIntExtra(EXTRA_IDX, -1);
					String toNum = intent.getStringExtra(EXTRA_TONUMBER);
					String sms = intent.getStringExtra(EXTRA_SMS);
					int succ = getResultCode();
					if (succ == Activity.RESULT_OK) {
						smsListView.getChildAt(idx).setBackgroundColor(
								getResources().getColor(R.color.green));
						adapter.notifyDataSetChanged();
						// deliveredSms++;
//						updateDataBase(toNum, sms, SEDNSUCCESSED);
						notifySuccessfulDelivery("Delivered to " + toNum
								+ " OK!", sms);
					} else {
						// TODO
					}
				}
			};

		if (smsIndexReceiver == null)
			smsIndexReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {

					int idx = intent.getIntExtra(INDEX_KEY, -1);
					smsListView.getChildAt(idx).setBackgroundColor(
							getResources().getColor(R.color.blue));
				}
			};
	}

	protected void registerReceivers() {
		this.registerReceiver(smsSentReceiver, new IntentFilter(SENT_ACTION));
		this.registerReceiver(smsDeliveredReceiver, new IntentFilter(
				DELIVERED_ACTION));
		this.registerReceiver(smsIndexReceiver, new IntentFilter(
				SMS_INDEX_ACTION));
	}

	protected void unregisterReceivers() {
		this.unregisterReceiver(smsSentReceiver);
		this.unregisterReceiver(smsDeliveredReceiver);
		this.unregisterReceiver(smsIndexReceiver);
	}

	// 加载发送进度条
	public void notifySuccessfulDelivery(String title, String text) {
		String ns = Context.NOTIFICATION_SERVICE;
		mNotificationManager = (NotificationManager) getSystemService(ns);

		int icon = R.drawable.ic_launcher;
		CharSequence tickerText = "HappyNewYear";
		long when = System.currentTimeMillis();
		mNotification = new Notification(icon, tickerText, when);

		if (maxSms > deliveredSms) {
			mNotification.flags = Notification.FLAG_ONGOING_EVENT;
		} else if (maxSms == deliveredSms) {
			mNotification.flags = Notification.FLAG_AUTO_CANCEL;
		}

		RemoteViews contentView = new RemoteViews(this.getPackageName(),
				R.layout.progressbar);
		contentView.setTextViewText(R.id.rate, deliveredSms + "/" + maxSms);
		contentView.setProgressBar(R.id.progress, maxSms, deliveredSms, false);
		mNotification.contentView = contentView;

		Intent notificationIntent = new Intent(this, SendListActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		mNotification.contentIntent = contentIntent;

		mNotification.defaults |= Notification.DEFAULT_SOUND;
		mNotification.defaults |= Notification.DEFAULT_VIBRATE;
		mNotification.defaults |= Notification.DEFAULT_LIGHTS;

		mNotificationManager.notify(HAPPYNEWYEAR_ID, mNotification);
	}

	protected void initdb() {
		dbHelper = new SQLiteOpenHelper(this, DB_NAME, null, DB_VERSION) {
			@Override
			public void onCreate(SQLiteDatabase db) {
				db.execSQL("create table sms (_id integer primary key autoincrement, "
						+ "to_number text not null, sms text not null, status text)");
			}

			@Override
			public void onUpgrade(SQLiteDatabase db, int oldVer, int newVer) {
			}
		};
		db = dbHelper.getWritableDatabase();
	}

	// 更新数据库
	protected void updateDataBase(String toNum, String sms, String sendState) {
		ContentValues values = new ContentValues();
		values.put(FIELD_TO, toNum);
		values.put(FIELD_SMS, sms);
		values.put(FIELD_SEND_STATE, sendState);

		String whereClause = "to_number=?";
		String[] whereArgs = { toNum };
		db.update(TBL_NAME, values, whereClause, whereArgs);
	}

	// 保存发送信息的数据
	protected void saveToDatabase(String toNum, String sms) {
		ContentValues values = new ContentValues();
		values.put(FIELD_TO, toNum);
		values.put(FIELD_SMS, sms);
		values.put(FIELD_SEND_STATE, "ok");
		db.insert(TBL_NAME, null, values);
	}

	/**
	 * Return all number ~ nickname pairs according to the rule. Be careful: the
	 * same numbers will be in only one pair.
	 * 
	 * @return <number, nickname>s
	 */
	public void readContacts(String market, String lang) {
		new AsyncReadContacts().execute(market, lang);
	}

	// 异步加载联系人
	private class AsyncReadContacts extends AsyncTask<String, String, Bundle> {

		@Override
		protected Bundle doInBackground(String... params) {

			String market = params[0];
			String lang = params[1];

			Cursor cur = getContentResolver().query(
					ContactsContract.Contacts.CONTENT_URI, null, null, null,
					null);

			// attributes for the contact
			Set<String> attrs = new HashSet<String>();

			while (cur.moveToNext()) {
				String contactId = cur.getString(cur
						.getColumnIndex(Contacts._ID));

				// retrieve phone numbers
				int phoneCount = cur.getInt(cur
						.getColumnIndex(Contacts.HAS_PHONE_NUMBER));

				// only process contacts with phone numbers
				if (phoneCount > 0) {

					Cursor notes = getContentResolver().query(
							Data.CONTENT_URI,
							new String[] { Data._ID, Note.NOTE },
							Data.CONTACT_ID + "=?" + " AND " + Data.MIMETYPE
									+ "='" + Note.CONTENT_ITEM_TYPE + "'",
							new String[] { contactId }, null);

					// retrieve all attributes from all notes
					attrs.clear();
					while (notes.moveToNext()) {
						String noteinfo = notes.getString(notes
								.getColumnIndex(Note.NOTE));
						String[] fragments = noteinfo.toUpperCase().split(","); // FIXME
																				// better
																				// regex?
						for (String attr : fragments) {
							attrs.add(attr);
						}
					}

					notes.close();

					// set defaults
					if (!attrs.contains(Market.NORTH.toString())
							&& !attrs.contains(Market.SOUTH.toString()))
						attrs.add(Market.NORTH.toString());

					if (!attrs.contains(Language.CHINESE.toString())
							&& !attrs.contains(Language.ENGLISH.toString()))
						attrs.add(Language.CHINESE.toString());

					// only process contacts with the matching market & language
					if (attrs.contains("ADC") // FIXME for class demo only
							&& (market.equals(Market.ANY) || attrs
									.contains(market.toString()))
							&& (lang.equals(Language.ANY) || attrs
									.contains(lang.toString()))) {

						Cursor phones = getContentResolver()
								.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
										null,
										Phone.CONTACT_ID + "=" + contactId,
										null, null);

						// process all phone numbers
						while (phones.moveToNext()) {
							String phoneNumber = phones.getString(phones
									.getColumnIndex(Phone.NUMBER));
							int phoneType = phones.getInt(phones
									.getColumnIndex(Phone.TYPE));

							if (isMobile(phoneNumber, phoneType)) {
								String nickname = null;
								Cursor nicknames = getContentResolver()
										.query(Data.CONTENT_URI,
												new String[] { Data._ID,
														Nickname.NAME },
												Data.CONTACT_ID
														+ "=?"
														+ " AND "
														+ Data.MIMETYPE
														+ "='"
														+ Nickname.CONTENT_ITEM_TYPE
														+ "'",
												new String[] { contactId },
												null);

								// only process contacts with nickname (the
								// first one)
								if (nicknames.moveToFirst()) {
									nickname = nicknames.getString(nicknames
											.getColumnIndex(Nickname.NAME));
								}

								nicknames.close();

								sendlist.putString(phoneNumber, nickname);
							}
						}

						phones.close();
					}
				}
			}
			cur.close();

			return sendlist;
		}

		@Override
		protected void onPostExecute(Bundle result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			Bundle data = SendListActivity.this.getIntent().getExtras();
			String cc = data.getString(HappyNewYearActivity.CUSTOMER_CARER);
			String tmpl = data.getString(HappyNewYearActivity.SMS_TEMPLATE);
			tmpl = tmpl.replaceAll("\\{FROM\\}", cc);
			for (String n : sendlist.keySet()) {
				String sms = tmpl.replaceAll("\\{TO\\}", sendlist.getString(n));
				Map<String, String> rec = new Hashtable<String, String>();
				rec.put(KEY_TO, n);
				rec.put(KEY_SMS, sms);
				smslist.add(rec);
				adapter.notifyDataSetChanged();
			}
			maxSms = sendlist.size();
		}
	}

	// the tricky pattern for identifying Chinese mobile numbers
	static final Pattern MOBILE_PATTERN = Pattern.compile("(13|15|18)\\d{9}");

	public boolean isMobile(String number, int type) {
		if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
			Matcher m = MOBILE_PATTERN.matcher(number);
			if (!m.find()) {
				return true;
			}
		}
		return false;
	}

	class sendSmsThread implements Runnable {

		private final String SMS_INDEX_ACTION = "sms_index_action";
		private final String INDEX_KEY = "index_key";
		private Context context = null;

		public sendSmsThread(Context context) {
			this.context = context;
		}

		public void run() {

			SmsManager sender = SmsManager.getDefault();
			if (sender == null) {
				Toast.makeText(SendListActivity.this, "can't get SmsManager!",
						Toast.LENGTH_SHORT).show();
			}

			try {
				for (int idx = 0; idx < smslist.size(); idx++) {

					Map<String, String> rec = smslist.get(idx);
					String toNumber = rec.get(KEY_TO);
					String sms = rec.get(KEY_SMS);
					Cursor cursor = db.query(TBL_NAME, new String[] { "_id" }, 
							"status='ok' and " + FIELD_TO+"=?",
							new String[]{toNumber}, null, null, null);
					if (cursor.moveToFirst()) {
						Message message = new Message();
						message.what = idx;
						handler.sendMessage(message);
						send_state = true;
						continue;
					}
					Intent intent = new Intent(SMS_INDEX_ACTION);
					intent.putExtra(INDEX_KEY, idx);
					context.sendBroadcast(intent);
					Thread.sleep(5000);

					// SMS sent pending intent
					Intent sentActionIntent = new Intent(SENT_ACTION);
					// sentActionIntent.putExtras(bundle);
					sentActionIntent.putExtra(EXTRA_IDX, idx);
					sentActionIntent.putExtra(EXTRA_TONUMBER, toNumber);
					sentActionIntent.putExtra(EXTRA_SMS, sms);
					PendingIntent sentPendingIntent = PendingIntent
							.getBroadcast(SendListActivity.this, 0,
									sentActionIntent,
									PendingIntent.FLAG_UPDATE_CURRENT);

					// SMS delivered pending intent
					Intent deliveredActionIntent = new Intent(DELIVERED_ACTION);
					// deliveredActionIntent.putExtras(bundle);
					deliveredActionIntent.putExtra(EXTRA_IDX, idx);
					deliveredActionIntent.putExtra(EXTRA_TONUMBER, toNumber);
					deliveredActionIntent.putExtra(EXTRA_SMS, sms);
					PendingIntent deliveredPendingIntent = PendingIntent
							.getBroadcast(SendListActivity.this, 0,
									deliveredActionIntent,
									PendingIntent.FLAG_UPDATE_CURRENT);

					sender.sendTextMessage(toNumber, null, sms,
							sentPendingIntent, deliveredPendingIntent);
				}
			} catch (InterruptedException e) {
			}
		}
	}

}

package info.liuqy.adc.happynewyear;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;

public class HappyNewYearActivity extends Activity {
	enum Market {
		NORTH, SOUTH, ANY;
		@Override
		public String toString() {
			switch (this) {
			case NORTH:
				return "NC";
			case SOUTH:
				return "SC";
			case ANY:
				return "";
			default:
				return super.toString();
			}
		}
	};

	enum Language {
		CHINESE, ENGLISH, ANY;
		@Override
		public String toString() {
			switch (this) {
			case CHINESE:
				return "CN";
			case ENGLISH:
				return "EN";
			case ANY:
				return "";
			default:
				return super.toString();
			}
		}
	};

	public static final String SENDLIST = "info.liuqy.adc.happynewyear.SENDLIST";
	public static final String CUSTOMER_CARER = "info.liuqy.adc.happynewyear.CUSTOMER_CARER";
	public static final String SMS_TEMPLATE = "info.liuqy.adc.happynewyear.SMS_TEMPLATE";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	public void genSendlist(View v) {
		RadioGroup rg = (RadioGroup) this.findViewById(R.id.customer_group);
		int id = rg.getCheckedRadioButtonId();
		Market targetMarket = (id == R.id.btn_north) ? Market.NORTH
				: Market.SOUTH;

		rg = (RadioGroup) this.findViewById(R.id.customer_lang);
		id = rg.getCheckedRadioButtonId();
		Language targetLanguage = (id == R.id.btn_cn) ? Language.CHINESE
				: Language.ENGLISH;

		Spinner sp = (Spinner) this.findViewById(R.id.customer_carer);
		String cc = sp.getSelectedItem().toString();

		EditText et = (EditText) this.findViewById(R.id.sms_template);
		String tmpl = et.getText().toString();

		Intent i = new Intent(this, SendListActivity.class);
		i.putExtra("mark", targetMarket.toString());
		i.putExtra("lang", targetLanguage.toString());
		i.putExtra(CUSTOMER_CARER, cc);
		i.putExtra(SMS_TEMPLATE, tmpl);
		startActivity(i);
	}

}
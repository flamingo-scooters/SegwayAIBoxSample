package com.segway.robot.sample.uart;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.segway.robot.datatransmit.BindStateListener;
import com.segway.robot.datatransmit.DataTransmitV1;
import com.segway.robot.datatransmit.exception.DataTransmitUnbindException;

public class MainActivity extends Activity {

    private EditText mEtAiResult;
    private TextView mTvResult;
    private boolean mIsBind;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEtAiResult = findViewById(R.id.et_ai_result);
        mTvResult = findViewById(R.id.tv_result);

        //bind service
        DataTransmitV1.getInstance().bindService(this, new BindStateListener() {
            @Override
            public void onBind() {
                mIsBind = true;
                try {
                    DataTransmitV1.getInstance().setDefaultAiSwitch(1);
                } catch (RemoteException | DataTransmitUnbindException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnbind(String reason) {
                mIsBind = false;
            }
        });

    }

    public void onClick(View view) {
        if (!mIsBind) {
            Toast.makeText(this, "Service Unbind!", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            switch (view.getId()) {
                case R.id.btn_set_ai_result:
                    String[] aiResultArr = mEtAiResult.getText().toString().trim().split(" ");
                    int aiInference = Integer.parseInt(aiResultArr[0]);
                    int aiPedestrian = Integer.parseInt(aiResultArr[1]);
                    ProtocolV1Util.sendAiResult(aiInference, aiPedestrian);
                    break;
                case R.id.btn_get_location:
                    LocationData locationData = ProtocolV1Util.getLocationData();
                    mTvResult.setText(locationData.toString());
                    break;
                case R.id.btn_get_speed:
                    WheelData wheelData = ProtocolV1Util.getWheelData();
                    mTvResult.setText(wheelData.toString());
                    break;
                default:
                    break;
            }
        }catch (Exception e) {
            Toast.makeText(this, "An Error Occurred", Toast.LENGTH_SHORT).show();
        }

    }
}

package com.segway.robot.sample.uart;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.segway.robot.service.AiBoxServiceManager;
import com.segway.robot.service.BindStateListener;
import com.segway.robot.service.execption.ServiceUnbindException;

public class MainActivity extends Activity {

    private EditText mEtAiResult;
    private TextView mTvResult;
    private boolean mIsBind;

    private Button mBtnSetAiResult;
    private Button mBtnGetLocation;
    private Button mBtnGetSpeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEtAiResult = findViewById(R.id.et_ai_result);
        mTvResult = findViewById(R.id.tv_result);

        mBtnSetAiResult = findViewById(R.id.btn_set_ai_result);
        mBtnGetLocation = findViewById(R.id.btn_get_location);
        mBtnGetSpeed = findViewById(R.id.btn_get_speed);

        mBtnSetAiResult.setOnClickListener(v ->
                {
                    if (!mIsBind) {
                        Toast.makeText(this, "Service Unbind!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        String[] aiResultArr = mEtAiResult.getText().toString().trim().split(" ");
                        int aiInference = Integer.parseInt(aiResultArr[0]);
                        int aiPedestrian = Integer.parseInt(aiResultArr[1]);
                        ProtocolV1Util.sendAiResult(aiInference, aiPedestrian);
                    } catch (Exception e) {
                        Toast.makeText(this, "An Error Occurred", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        mBtnGetLocation.setOnClickListener(v ->
                {
                    if (!mIsBind) {
                        Toast.makeText(this, "Service Unbind!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        LocationData locationData = ProtocolV1Util.getLocationData();
                        mTvResult.setText(locationData != null ? locationData.toString() : "no location");
                    } catch (Exception e) {
                        Toast.makeText(this, "An Error Occurred", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        mBtnGetSpeed.setOnClickListener(v ->
                {
                    if (!mIsBind) {
                        Toast.makeText(this, "Service Unbind!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        WheelData wheelData = ProtocolV1Util.getWheelData();
                        mTvResult.setText(wheelData != null ? wheelData.toString() : "no speed");
                    } catch (Exception e) {
                        Toast.makeText(this, "An Error Occurred", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        //bind service
        AiBoxServiceManager.getInstance().bindService(this, new BindStateListener() {
            @Override
            public void onBind() {
                mIsBind = true;
                try {
                    AiBoxServiceManager.getInstance().getDataTransmit().setDefaultAiSwitch(1);
                } catch (RemoteException | ServiceUnbindException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnbind(String reason) {
                mIsBind = false;
            }
        });

    }
}

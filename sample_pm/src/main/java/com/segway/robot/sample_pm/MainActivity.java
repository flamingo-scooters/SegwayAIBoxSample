package com.segway.robot.sample_pm;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.segway.robot.service.AiBoxServiceManager;
import com.segway.robot.service.BindStateListener;
import com.segway.robot.service.execption.ServiceUnbindException;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private boolean mIsBind;
    private EditText mEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "MainActivity onCreate ");
        setContentView(R.layout.activity_main);
        mEditText = findViewById(R.id.apk_path);
        //setup 1: bind service
        AiBoxServiceManager.getInstance().bindService(this, new BindStateListener() {
            @Override
            public void onBind() {
                mIsBind = true;
                Toast.makeText(MainActivity.this, "Service Bind", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUnbind(String reason) {
                mIsBind = false;
                Toast.makeText(MainActivity.this, "Service Unbind", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AiBoxServiceManager.getInstance().unbindService();
    }

    public void onClick(View view) {
        if (view.getId() == R.id.btn_install) {
            try {
                Editable text = mEditText.getText();
                if (text != null && !TextUtils.isEmpty(text.toString())) {
                    installApp(text.toString());
                }
            } catch (Exception e) {
                Log.d(TAG, "install", e);
            }
        }
    }

    private void installApp(final String path) {
        if (!mIsBind) {
            Toast.makeText(this, "Service not bound", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String[] spit = path.split(" ");
                    String path = spit[0];
                    int type = 0;
                    String pkgName = null;
                    String className = null;
                    String action = null;
                    Intent intent = null;
                    if (spit.length == 4) {
                        intent = new Intent();
                        type = Integer.parseInt(spit[1]);
                        pkgName = spit[2];
                        className = spit[3];
                    } else if (spit.length == 5) {
                        intent = new Intent();
                        type = Integer.parseInt(spit[1]);
                        pkgName = spit[2];
                        className = spit[3];
                        action = spit[4];
                    }

                    if (intent != null) {
                        switch (type) {
                            case 0:
                            case 1:
                                intent.setComponent(new ComponentName(pkgName, className));
                                break;
                            case 2:
                                intent.setAction(action);
                                intent.setComponent(new ComponentName(pkgName, className));
                                break;
                            default:
                                break;
                        }
                    }

                    //setup 2: call method installApp
                    boolean ret = AiBoxServiceManager.getInstance().getPackageManager().installApp(path, intent, type);
                    Log.d(TAG, "install ret: " + ret);
                } catch (ServiceUnbindException | RemoteException e) {
                    Log.d(TAG, "installApp", e);
                    Toast.makeText(MainActivity.this, "An error has occurred", Toast.LENGTH_SHORT).show();
                }
            }
        }).start();

    }
}

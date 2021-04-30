package com.petkit.matetool.ui.W5;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.petkit.android.ble.BLEConsts;
import com.petkit.android.ble.DeviceInfo;
import com.petkit.android.ble.service.AndroidBLEActionService;
import com.petkit.android.utils.CommonUtils;
import com.petkit.android.utils.Consts;
import com.petkit.android.utils.PetkitLog;
import com.petkit.android.widget.LoadDialog;
import com.petkit.matetool.R;
import com.petkit.matetool.model.Device;
import com.petkit.matetool.model.Tester;
import com.petkit.matetool.ui.W5.mode.W5TestUnit;
import com.petkit.matetool.ui.W5.utils.W5Utils;
import com.petkit.matetool.ui.aq.AQScanListAdapter;
import com.petkit.matetool.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * Created by Jone on 17/4/24.
 */
public class W5ScanActivity extends BaseActivity implements View.OnClickListener {

    private Tester mTester;
    private int mTestType;
    private int mW5Type;
    private Device mCurDevice, mErrorDevice;

    private ArrayList<W5TestUnit> mW5TestUnits;

    private ListView mListView;
    private AQScanListAdapter mListAdapter;

    private ImageView scanImageView;
    private TextView scanPromptTextView;
    private Animation rotateAnimation;
    private FrameLayout scanFrameLayout;

    private boolean scanState = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null) {
            mTester = (Tester) savedInstanceState.getSerializable(W5Utils.EXTRA_W5_TESTER);
            mTestType = savedInstanceState.getInt("TestType");
            mW5Type = savedInstanceState.getInt(W5Utils.EXTRA_W5_TYPE);
            mErrorDevice = (Device) savedInstanceState.getSerializable(W5Utils.EXTRA_W5);
        } else {
            mTester = (Tester) getIntent().getSerializableExtra(W5Utils.EXTRA_W5_TESTER);
            mTestType = getIntent().getIntExtra("TestType", W5Utils.TYPE_TEST);
            mErrorDevice = (Device) getIntent().getSerializableExtra(W5Utils.EXTRA_W5);
            mW5Type = getIntent().getIntExtra(W5Utils.EXTRA_W5_TYPE, W5Utils.W5_TYPE_NORMAL);
        }

        setContentView(R.layout.activity_go_test);

        registerBoradcastReceiver();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable(W5Utils.EXTRA_W5_TESTER, mTester);
        outState.putInt("TestType", mTestType);
        outState.putSerializable(W5Utils.EXTRA_W5, mErrorDevice);
        outState.putInt(W5Utils.EXTRA_W5_TYPE, mW5Type);
    }


    @Override
    protected void setupViews() {
        setTitle(mW5Type == W5Utils.W5_TYPE_NORMAL ? "智能饮水机（W5）测试" : "智能饮水机mini（W5C）测试");

        mW5TestUnits = W5Utils.generateW5TestUnitsForType(mTestType);

        scanImageView = (ImageView) findViewById(R.id.scan_img);
        scanImageView.setOnClickListener(this);
        scanPromptTextView = (TextView) findViewById(R.id.scan_prompt_text);
        scanFrameLayout = (FrameLayout) findViewById(R.id.scan_view);

        rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.scan_rotate);
        LinearInterpolator lin = new LinearInterpolator();
        rotateAnimation.setInterpolator(lin);

        mListView = (ListView) findViewById(R.id.list);
        mListView.setScrollbarFadingEnabled(true);
        mListView.setDividerHeight(1);

        mListAdapter = new AQScanListAdapter(this, null);
        mListView.setAdapter(mListAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> view, View view1, int i, long l) {
                startConnectDevice(mListAdapter.getItem(i));
            }
        });

        startScanDevice();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.scan_img:
                startScanDevice();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Intent intent = new Intent(BLEConsts.BROADCAST_ACTION);
        intent.putExtra(BLEConsts.EXTRA_ACTION, BLEConsts.ACTION_ABORT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        unregisterBroadcastReceiver();
    }

    private void startConnectDevice(DeviceInfo deviceInfo) {
        showLoadDialog();

        mCurDevice = new Device(deviceInfo.getMac(), deviceInfo.getSn(), "");

        Intent intent = new Intent(BLEConsts.BROADCAST_ACTION);
        intent.putExtra(BLEConsts.EXTRA_ACTION, BLEConsts.ACTION_ABORT);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        Bundle bundle = new Bundle();
        bundle.putInt(BLEConsts.EXTRA_ACTION, BLEConsts.BLE_ACTION_W5_TEST);
        bundle.putSerializable(BLEConsts.EXTRA_DEVICE_INFO, deviceInfo);
        startBLEAction(bundle);
    }

    public void showLoadDialog(){
        LoadDialog.show(this, "正在连接设备", false, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Intent intent = new Intent(BLEConsts.BROADCAST_ACTION);
                intent.putExtra(BLEConsts.EXTRA_ACTION, BLEConsts.ACTION_ABORT);
                LocalBroadcastManager.getInstance(W5ScanActivity.this).sendBroadcast(intent);
            }
        });
    }

    private void startScanDevice() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            CommonUtils.showShortToast(this, "你的手机蓝牙不支持");
            return;
        }

        if (scanState) {
            return;
        }

        mListAdapter.clearList();

        if(scanFrameLayout.getChildCount() > 2) {
            scanFrameLayout.removeViewsInLayout(2, scanFrameLayout.getChildCount() - 2);
        }

        scanImageView.startAnimation(rotateAnimation);
        scanState = true;

        Bundle bundle = new Bundle();
        bundle.putInt(BLEConsts.EXTRA_ACTION, BLEConsts.BLE_ACTION_SCAN);

        startBLEAction(bundle);

        CommonUtils.addSysIntMap(this, Consts.SHARED_BLE_STATE, Consts.BLE_STATE_USING);

        scanPromptTextView.setText("正在搜索附近的设备…");
    }

    private void startBLEAction(Bundle bundle){
        if(CommonUtils.getAndroidSDKVersion() >= 18){
            if (!getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_BLUETOOTH_LE)) {
                return;
            }

            final Intent service = new Intent(this, AndroidBLEActionService.class);
            service.putExtras(bundle);
            startService(service);
        }else {
            CommonUtils.showShortToast(this, "你的手机蓝牙不支持");
        }
    }


    private void addDevice(DeviceInfo device) {

        mListAdapter.add(device);

        scanPromptTextView.setText("搜索到的设备：");
    }

    private void scanFinish() {
        scanImageView.clearAnimation();
        scanState = false;

        if(mListAdapter.getCount() == 0) {
            scanPromptTextView.setText("没找到附近的设备，请点击雷达重新搜索");
        } else {
            scanPromptTextView.setText("点击雷达可以重新搜索");
        }
    }


    private void showResultDialog() {
        LoadDialog.dismissDialog();
        new AlertDialog.Builder(this)
                .setCancelable(false)
                .setTitle(R.string.Prompt)
                .setMessage("设备（mac:" + mCurDevice.getMac() + "）已进入整机测试模式，请按顺序完成测试项！")
                .setPositiveButton("确认",
                        new DialogInterface.OnClickListener(){
                            public void onClick(
                                    DialogInterface dialog,
                                    int which){

                            }
                        })
                .show();
    }

    private void entryDetailTestActivity() {
        LoadDialog.dismissDialog();

        Intent intent = new Intent(this, W5TestMainActivity.class);
        intent.putExtra("TestUnits", mW5TestUnits);
        intent.putExtra(W5Utils.EXTRA_W5, mCurDevice);
        intent.putExtra(W5Utils.EXTRA_ERROR_W5, mErrorDevice);
        intent.putExtra(W5Utils.EXTRA_W5_TESTER, mTester);
        intent.putExtra(W5Utils.EXTRA_W5_TYPE, mW5Type);
        intent.putExtra("TestType", mTestType);
        startActivityForResult(intent, 0x12);
    }

    private BroadcastReceiver mBroadcastReceiver;

    private void registerBoradcastReceiver() {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                PetkitLog.d("" + arg1.getAction());

                switch (arg1.getAction()) {
                    case BLEConsts.BROADCAST_PROGRESS:
                        int progress = arg1.getIntExtra(BLEConsts.EXTRA_PROGRESS, 0);

                        switch (progress) {
                            case BLEConsts.PROGRESS_CONNECTED:
                                entryDetailTestActivity();
                                break;
                            case BLEConsts.PROGRESS_SCANING_TIMEOUT:
                            case BLEConsts.PROGRESS_SCANING_FAILED:
                            case BLEConsts.ERROR_DEVICE_DISCONNECTED:
                            case BLEConsts.PROGRESS_DISCONNECTING:
                            case BLEConsts.ERROR_SYNC_INIT_FAIL:
                            case BLEConsts.ERROR_DEVICE_ID_NULL:
                                scanFinish();
                                break;
                            default:
                                break;
                        }
                        break;

                    case BLEConsts.BROADCAST_ERROR:
                        progress = arg1.getIntExtra(BLEConsts.EXTRA_DATA, 0);

                        switch (progress) {
                            case BLEConsts.ERROR_ABORTED:
                                break;
                            case BLEConsts.ERROR_INVALID_PARAMETERS:
                            case BLEConsts.ERROR_INVALID_RESPONSE:
                            case BLEConsts.ERROR_SYNC_TIMEOUT:
                            default:
                                LoadDialog.dismissDialog();
                                CommonUtils.showShortToast(W5ScanActivity.this, "设备已断开");
                                break;
                        }
                        break;
                    case BLEConsts.BROADCAST_SCANED_DEVICE:
                        DeviceInfo deviceInfo = (DeviceInfo) arg1.getSerializableExtra(BLEConsts.EXTRA_DEVICE_INFO);

                        if(deviceInfo.getName() == null){
                            return;
                        }

                        if (deviceInfo.getName().equalsIgnoreCase(BLEConsts.W5_DISPLAY_NAME)) {
                            List<DeviceInfo> list = mListAdapter.getList();
                            for (DeviceInfo deviceInfos : list) {
                                if (deviceInfo.getMac()!=null){
                                    if (deviceInfos.getMac().equals(deviceInfo.getMac())) {
                                        return;
                                    }
                                }
                            }

                            addDevice(deviceInfo);
                        }
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BLEConsts.BROADCAST_PROGRESS);
        filter.addAction(BLEConsts.BROADCAST_ERROR);
        filter.addAction(BLEConsts.BROADCAST_LOG);
        filter.addAction(BLEConsts.BROADCAST_SCANED_DEVICE);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);
    }

    private void unregisterBroadcastReceiver() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

}

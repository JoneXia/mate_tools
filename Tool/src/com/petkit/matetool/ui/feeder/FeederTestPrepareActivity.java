package com.petkit.matetool.ui.feeder;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;
import com.petkit.android.http.AsyncHttpUtil;
import com.petkit.android.utils.CommonUtils;
import com.petkit.android.utils.Consts;
import com.petkit.android.widget.LoadDialog;
import com.petkit.matetool.R;
import com.petkit.matetool.http.ApiTools;
import com.petkit.matetool.http.AsyncHttpRespHandler;
import com.petkit.matetool.ui.base.BaseActivity;
import com.petkit.matetool.ui.feeder.mode.FeederTester;
import com.petkit.matetool.ui.feeder.mode.FeedersError;
import com.petkit.matetool.ui.feeder.utils.FeederUtils;
import com.petkit.matetool.utils.FileUtils;
import com.petkit.matetool.utils.JSONUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import cz.msebera.android.httpclient.Header;

import static com.petkit.matetool.ui.feeder.utils.FeederUtils.FILE_CHECK_INFO_NAME;
import static com.petkit.matetool.ui.feeder.utils.FeederUtils.FILE_MAINTAIN_INFO_NAME;

/**
 * 喂食器测试，测试准备，需要登录，已经缓存数据处理
 *
 * Created by Jone on 17/4/19.
 */
public class FeederTestPrepareActivity extends BaseActivity {

    private FeederTester mTester;
    private EditText nameEdit, pwEdit;
    private TextView promptText, testerInfoTextView;
    private Button actionBtn, uploadBtn;

    private FeedersError mFeedersError;
    private boolean isLogining;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_feeder_prepare);

        ApiTools.setApiBaseUrl();
    }

    @Override
    protected void setupViews() {
        setTitle(R.string.Title_prepare);

        promptText = (TextView) findViewById(R.id.test_prompt);
        actionBtn = (Button) findViewById(R.id.login);
        actionBtn.setOnClickListener(this);
        uploadBtn = (Button) findViewById(R.id.upload);
        uploadBtn.setOnClickListener(this);
        nameEdit = (EditText) findViewById(R.id.input_login_name);
        pwEdit = (EditText) findViewById(R.id.input_login_password);
        testerInfoTextView = (TextView) findViewById(R.id.tester_info);

        findViewById(R.id.logout).setOnClickListener(this);

        String tester = CommonUtils.getSysMap(FeederUtils.SHARED_FEEDER_TESTER);
        if(!isEmpty(tester)) {
            mTester = new Gson().fromJson(tester, FeederTester.class);
            if (!isEmpty(mTester.getName())) {
                testerInfoTextView.setText("当前用户名：" + mTester.getName());
            }
            AsyncHttpUtil.addHttpHeader("F-Session", CommonUtils.getSysMap(Consts.SHARED_SESSION_ID));
        }

        mFeedersError = FeederUtils.getFeedersErrorMsg();
    }

    @Override
    protected void onResume() {
        super.onResume();

        updateView();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.login:
                if(mTester != null) {
                    if (FeederUtils.checkHasSnCache()) {
                        LoadDialog.show(this);
                        startUploadSn();
                    } else {
                        startTest();
                    }
                } else {
                    String name = nameEdit.getEditableText().toString();
                    String pw = pwEdit.getEditableText().toString();
                    if(isEmpty(name)) {
                        showShortToast("请输入用户名");
                        return;
                    }
                    if(isEmpty(pw)) {
                        showShortToast("请输入密码");
                        return;
                    }
                    login(name, pw);
                }
                break;
            case R.id.logout:
                if(mFeedersError != null) {
                    showShortToast("还有异常数据需要处理，完成后才能登出！");
                } else if(FeederUtils.checkHasSnCache()) {
                    showShortToast("还有未上传的测试数据，需要先上传完成才能登出！");
                } else {
                    logout();
                }
                break;
            case R.id.upload:
                if(mFeedersError != null) {
                    Bundle bundle = new Bundle();
                    bundle.putSerializable(FeederUtils.EXTRA_FEEDER_TESTER, mTester);
                    startActivityWithData(FeederErrorListActivity.class, bundle, false);
                } else {
                    LoadDialog.show(this);
                    startUploadSn();
                }
                break;
        }
    }

    private void updateView () {
        if(mTester != null) {
            mFeedersError = FeederUtils.getFeedersErrorMsg();
            if(mFeedersError != null) {
                promptText.setText("检测到异常数据，需要先处理，才能开始测试！");
                actionBtn.setVisibility(View.GONE);
                uploadBtn.setVisibility(View.VISIBLE);
                uploadBtn.setText("处理异常");
            } else if(FeederUtils.checkHasSnCache()) {
                promptText.setText("还有未上传的测试数据，需要先上传完成才能开始测试！");
                actionBtn.setVisibility(View.GONE);
                uploadBtn.setVisibility(View.VISIBLE);
                uploadBtn.setText("开始上传");
            } else {
                promptText.setText("可以开始测试啦！");
                actionBtn.setText("进入测试");
                actionBtn.setVisibility(View.VISIBLE);

                File dir = new File(CommonUtils.getAppCacheDirPath() + ".sn/");
                String[] files = dir.list();
                if(files != null && files.length > 0) {
                    uploadBtn.setVisibility(View.VISIBLE);
                    uploadBtn.setText("开始上传");
                } else {
                    uploadBtn.setVisibility(View.GONE);
                }
            }
            nameEdit.setVisibility(View.INVISIBLE);
            pwEdit.setVisibility(View.INVISIBLE);

            findViewById(R.id.logout).setVisibility(View.VISIBLE);
        } else {
            actionBtn.setText(R.string.Login);
            actionBtn.setVisibility(View.VISIBLE);
            promptText.setText("请先完成登录");
            nameEdit.setVisibility(View.VISIBLE);
            pwEdit.setVisibility(View.VISIBLE);
            uploadBtn.setVisibility(View.GONE);
            findViewById(R.id.logout).setVisibility(View.INVISIBLE);
        }
    }

    private void startTest() {
        Bundle bundle = new Bundle();
        bundle.putSerializable(FeederUtils.EXTRA_FEEDER_TESTER, mTester);
        startActivityWithData(FeederStartActivity.class, bundle, false);
    }


    private void login(final String name, String pw) {
        LoadDialog.show(this);

        HashMap<String, String> params = new HashMap<>();
        params.put("username", name);
        params.put("password", pw);
        params.put("device", getDeviceInfo(this));

        AsyncHttpUtil.post("/api/login", params, new AsyncHttpRespHandler(this) {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                super.onSuccess(statusCode, headers, responseBody);

                JSONObject jsonObject = JSONUtils.getJSONObject(responseResult);
                JSONObject dataObj = JSONUtils.getJSONObject(jsonObject, "data");
                if(dataObj != null) {
                    try {
                        String token = JSONUtils.getValue(dataObj, "token");
                        long timestamp = dataObj.getLong("timestamp");
                        String factory = dataObj.getString("factory");
                        String station = dataObj.getString("station");
                        if(Math.abs(System.currentTimeMillis() - timestamp) < 60 * 60 * 1000) {
                            mTester = new FeederTester();
                            mTester.setCode(factory);
                            mTester.setStation(station);
                            mTester.setName(name);
                            CommonUtils.addSysMap(Consts.SHARED_SESSION_ID, token);
                            CommonUtils.addSysMap(FeederUtils.SHARED_FEEDER_TESTER, new Gson().toJson(mTester));
                            AsyncHttpUtil.addHttpHeader("F-Session", token);
                            testerInfoTextView.setText("当前用户名：" + mTester.getName());

                            if(FeederUtils.checkHasSnCache()) {
                                isLogining = true;
                                startUploadSn();
                            } else {
                                getLastSN();
                            }
                        } else {
                            showShortToast("系统时间不对，请先设置！");
                            loginFailed();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        loginFailed();
                    }
                } else {
                    loginFailed();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                super.onFailure(statusCode, headers, responseBody, error);

                switch (statusCode) {
                    case 463:
                        showShortToast("密码错误");
                        break;
                    case 461:
                        showShortToast("账号被锁定，不能在多个设备上登录同一个账号！");
                        break;
                    case 462:
                        showShortToast("账号已被禁用！");
                        break;
                    case 464:
                        showShortToast("账号不存在！");
                        break;
                    default:
                        showShortToast("登录失败！");
                        break;
                }
                loginFailed();
            }
        });
    }

    private void logout() {
        HashMap<String, String> params = new HashMap<>();
        params.put("device", getDeviceInfo(this));

        AsyncHttpUtil.post("/api/logout", params, new AsyncHttpRespHandler(this, true) {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                super.onSuccess(statusCode, headers, responseBody);

                loginFailed();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                super.onFailure(statusCode, headers, responseBody, error);

                switch (statusCode) {
                    case 463:
                        showShortToast("密码错误");
                        break;
                    case 461:
                        showShortToast("账号被锁定，不能在多个设备上登录同一个账号！");
                        break;
                    case 462:
                        showShortToast("账号已被禁用！");
                        break;
                    case 464:
                        showShortToast("账号不存在！");
                        break;
                    default:
                        showShortToast("登出失败！");
                        break;
                }
            }
        });
    }

    private void getLastSN() {
        AsyncHttpUtil.get("/api/sn/latest", new AsyncHttpRespHandler(this) {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                super.onSuccess(statusCode, headers, responseBody);

                isLogining = false;

                JSONObject jsonObject = JSONUtils.getJSONObject(responseResult);
                JSONObject dataObj = JSONUtils.getJSONObject(jsonObject, "data");
                try {
                    if(jsonObject.getInt("code") == 0 && dataObj != null && !dataObj.isNull("sn")) {
                        String sn = dataObj.getString("sn");
                        FeederUtils.initSnSerializableNumber(sn);
                    }
                    LoadDialog.dismissDialog();
                    updateView();
                } catch (JSONException e) {
                    e.printStackTrace();
                    loginFailed();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                super.onFailure(statusCode, headers, responseBody, error);

                loginFailed();
            }
        });
    }

    private void loginFailed() {
        LoadDialog.dismissDialog();
        CommonUtils.addSysMap(Consts.SHARED_SESSION_ID, "");
        AsyncHttpUtil.addHttpHeader("F-Session", "");
        CommonUtils.addSysMap(FeederUtils.SHARED_FEEDER_TESTER, "");
        mTester = null;
        updateView();
    }

    private void startUploadSn() {
        File dir = new File(CommonUtils.getAppCacheDirPath() + ".sn/");
        String[] files = dir.list();

        if(files != null && files.length > 0) {
            uploadSn(new File(dir, files[0]));
        } else if (isLogining) {
            getLastSN();
        } else {
            LoadDialog.dismissDialog();
            showShortToast("上传完成");
            updateView();
        }
    }

    private void uploadSnFailed() {
        LoadDialog.dismissDialog();
    }

    private void uploadSn(final File file) {
        String content = FileUtils.readFileToString(file);

        if(isEmpty(content)) {
            file.delete();
            startUploadSn();
            return;
        }

        String api;
        if(FILE_MAINTAIN_INFO_NAME.equals(file.getName())) {
            api = "/api/sn/maintain/repair";
        } else if(FILE_CHECK_INFO_NAME.equals(file.getName())) {
            api = "/api/sn/maintain/inspect";
        } else {
            api = "/api/sn/batch";
        }

        HashMap<String, String> params = new HashMap<>();
        params.put("snList", "{\"snList\":[" + content.substring(0, content.length() - 1) + "]}");

        AsyncHttpUtil.post(api, params, new AsyncHttpRespHandler(this) {

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                super.onSuccess(statusCode, headers, responseBody);

                if (statusCode == 200) {
                    try {
                        JSONObject result = JSONUtils.getJSONObject(responseResult);
                        if (!result.isNull("code")) {
                            int code = result.getInt("code");
                            switch (code) {
                                case 0:
                                    file.delete();
                                    startUploadSn();
                                    break;
                                default:
                                    if(!result.isNull("data")) {
                                        mFeedersError = gson.fromJson(result.getString("data"), FeedersError.class);
                                        FeederUtils.storeDuplicatedInfo(mFeedersError);
                                        file.delete();
                                        if (isLogining) {
                                            getLastSN();
                                        } else {
                                            LoadDialog.dismissDialog();
                                            updateView();
                                        }
                                    } else {
                                        file.delete();
                                        startUploadSn();
                                    }
                                    break;
                            }
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    uploadSnFailed();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                super.onFailure(statusCode, headers, responseBody, error);
                uploadSnFailed();
            }
        });
    }


    private String getDeviceInfo(Context context) {
        String m_szDevIDShort = "35" + //we make this look like a valid IMEI
                Build.BOARD.length()%10 + Build.BRAND.length()%10 +
                Build.CPU_ABI.length()%10 + Build.DEVICE.length()%10 +
                Build.DISPLAY.length()%10 + Build.HOST.length()%10 +
                Build.ID.length()%10 + Build.MANUFACTURER.length()%10 +
                Build.MODEL.length()%10 + Build.PRODUCT.length()%10 +
                Build.TAGS.length()%10 + Build.TYPE.length()%10 + Build.USER.length()%10 ; //13 digits

        String m_szAndroidID = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);

        WifiManager wm = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
        String m_szWLANMAC = wm.getConnectionInfo().getMacAddress();

        BluetoothAdapter m_BluetoothAdapter = BluetoothAdapter.getDefaultAdapter();; // Local Bluetooth adapter
        String m_szBTMAC = m_BluetoothAdapter.getAddress();

        String m_szLongID = m_szDevIDShort + m_szAndroidID+ m_szWLANMAC + m_szBTMAC;
        // compute md5
        MessageDigest m = null;
        try {
            m = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        m.update(m_szLongID.getBytes(),0,m_szLongID.length());
        // get md5 bytes
        byte p_md5Data[] = m.digest();
        // create a hex string
        String m_szUniqueID = new String();
        for (int i=0;i<p_md5Data.length;i++) {
            int b =  (0xFF & p_md5Data[i]);
            // if it is a single digit, make sure it have 0 in front (proper padding)
            if (b <= 0xF)
                m_szUniqueID+="0";
            // add number to string
            m_szUniqueID+=Integer.toHexString(b);
        }   // hex string to uppercase
        m_szUniqueID = m_szUniqueID.toUpperCase();
        return m_szUniqueID;
    }

}

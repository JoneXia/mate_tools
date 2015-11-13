package com.petkit.android.ble;

import java.io.Serializable;

public class WifiInfo implements Serializable {
	private static final long serialVersionUID = -4523460236390521750L;
	
	private String ssid;
	private String capabilities;
	private String address;
	private String bssid;
	private int password;
	private int level;

	public WifiInfo() {
		super();
	}
	
	public String getAddress() {
		return address;
	}
	
	public void setAddress(String address) {
		this.address = address;
	}

	public String getSSID() {
		return ssid;
	}
	
	public void setSSID(String ssid) {
		this.ssid = ssid;
	}
	
	public String getBSSID() {
		return bssid;
	}

	public void setBSSID(String bssid) {
		this.bssid = bssid;
	}
	
	public int getPassword() {
		return password;
	}

	public void setPassword(int password) {
		this.password = password;
	}
	
	public String getCapabilities() {
		return capabilities;
	}

	public void setCapabilities(String capabilities) {
		this.capabilities = capabilities;
	}
	
	public int getLevel() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
	}
}
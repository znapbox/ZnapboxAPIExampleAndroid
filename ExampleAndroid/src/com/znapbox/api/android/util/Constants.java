package com.znapbox.api.android.util;

public class Constants {
	
	//server name
	public static final String SERVER_NAME = "http://znapboxapi.cloudapp.net";
	public static final String QUERY_URL = SERVER_NAME + "/searcher/api/search/image/";

	//width of image that will be sent to server
	public static final int IMAGE_WIDTH = 500;
	
	// Maximum duration of a remote search.
	public static final long REMOTE_MATCH_MAX_DURATION = 30000;
	
	//Splash screen duration
	public static final int SPLASH_TIME = 2000;
	
	public static final String ZNAPBOX_PREFERENCES = "ZNAPBOX_PREFERENCES";
	public static final String ZNAPBOX_APIKEY = "ZNAPBOX_APIKEY";
	
	//API constants
	public static final String FOUND = "FOUND";
	public static final String NOT_FOUND = "NOT_FOUND";
	public static final String INVALID_APIKEY = "INVALID_APIKEY";
	
	public static final String FILE_PARAMETER = "file";
	public static final String API_KEY_PARAMETER = "apikey";
	
	
}

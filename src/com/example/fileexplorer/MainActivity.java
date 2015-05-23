package com.example.fileexplorer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;

import org.apache.http.conn.util.InetAddressUtils;

import com.scnuly.utils.FileUtils;

import android.support.v7.app.ActionBarActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {

	private Button buttonOpen = null;
	private Button buttonSent = null;
	private Button buttonReceive = null;
	private EditText editText = null;
	private ProgressBar fileProgress = null;
	private TextView ipAddr = null;
	private String localIp = null;
	
	static final int HANDLER_UPDATE_UI = 1;  
	static final int HANDLER_UPDATE_PROGRESSBAR = 2; 
	static final int HANDLER_UPDATE_PROGRESSSTATE = 3;
	static final int HANDLER_UPDATE_CONNECT = 4;
	
	Handler threadMsg = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			// TODO Auto-generated method stub
			switch(msg.what) {
			case HANDLER_UPDATE_UI:
				buttonSent.setEnabled(true);
				break;
			case HANDLER_UPDATE_CONNECT:
				String str = (String)msg.obj;
				ipAddr.setText("已连接IP：" + str);
				break;
			case HANDLER_UPDATE_PROGRESSBAR:
				fileProgress.setMax(msg.arg1);
				break;
			case HANDLER_UPDATE_PROGRESSSTATE:
				fileProgress.setProgress(msg.arg1);
				break;
			}
			super.handleMessage(msg);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		buttonOpen = (Button)findViewById(R.id.buttonOpen);
		buttonSent = (Button)findViewById(R.id.buttonSent);
		buttonReceive = (Button)findViewById(R.id.buttonReceive);
		editText = (EditText)findViewById(R.id.editTextfile);
		fileProgress = (ProgressBar)findViewById(R.id.progressBarFile);
		ipAddr = (TextView)findViewById(R.id.textIPaddr);
			
		System.out.println("tiger MainActivity create");
		editText.setText("/storage/emulated/0");
		localIp = getLocalHostIp();
		if(localIp==null || localIp.equals("127.0.0.1")) {
			ipAddr.setText("WIFI未连接");
			buttonOpen.setEnabled(false);
			buttonSent.setEnabled(false);
			buttonReceive.setEnabled(false);
			editText.setEnabled(false);
		}
		else {
			ipAddr.setText("本机IP：" + localIp);
		}
		
		buttonOpen.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				Intent intent = new Intent();
				intent.setClass(MainActivity.this, FilelistActivity.class);
				intent.putExtra("DIR_PATH", editText.getText().toString());
				MainActivity.this.startActivityForResult(intent, 1);
			}
		});
		
		buttonSent.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				System.out.println("tiger sent ready to start");			
				buttonSent.setEnabled(false);
				fileProgress.setProgress(0);
				new tcpSorketClientThread(0).start();
			}			
		});
	}


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onActivityResult(int arg0, int arg1, Intent arg2) {
		// TODO Auto-generated method stub
		//super.onActivityResult(arg0, arg1, arg2);
		String result = arg2.getExtras().getString("result");
		editText.setText(result);
	}
	
	public String getIpHead(String ip) {
		byte[] bufSrc = ip.getBytes();
		int i=0, n=0;

		for(i=0; i<bufSrc.length; i++){
			if(n==3)
				break;
			if(bufSrc[i]=='.')
				n++;
		}
		
		byte[] bufDst = new byte[i];
		System.arraycopy(bufSrc, 0, bufDst, 0, i);
		return (new String(bufDst));
	}
	
	public String getLocalHostIp() {
		String ipaddress = "";
		try {
			Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces();
			
			while(en.hasMoreElements()) {
				NetworkInterface nif = en.nextElement();
				Enumeration<InetAddress> inet = nif.getInetAddresses();
				//System.out.println("tiger " + nif.getName());
				while(inet.hasMoreElements()) {
					InetAddress ip = inet.nextElement();
					//System.out.println("tiger " + ip.getHostAddress());
					if(!ip.isLinkLocalAddress() && 
							InetAddressUtils.isIPv4Address(ip.getHostAddress())) {
						return ipaddress = ip.getHostAddress();
					}
				}
			}
			
		} catch (SocketException e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		return ipaddress;
	}
	
	class filePackage {
		private byte[] buf = null;
		private static final int FILENAMELEN = 256;
		
		public filePackage(String fileName, long nFileLength) {
			buf = new byte[4 + FILENAMELEN];
			//copy file length
			buf[0] = (byte)(nFileLength & 0xff);
			buf[1] = (byte)(nFileLength >>8 & 0xff);
			buf[2] = (byte)(nFileLength >>16 & 0xff);
			buf[3] = (byte)(nFileLength >>24 & 0xff);
			
			//copy file name
			System.arraycopy(fileName.getBytes(), 0, buf, 4, fileName.getBytes().length);
			buf[4 + FILENAMELEN - 1] = 0;
			buf[4 + FILENAMELEN - 2] = 0;
		}
		
		public byte[] getBytes() {
			return buf;
		}
	}
	
	class tcpSorketClientThread extends Thread {
		
		private int ip = 0;
		public tcpSorketClientThread(int n) {
			ip = n;
		}
		
		public void run() {
			// TODO Auto-generated method stub
			InputStream fileInput, input = null;
			OutputStream output = null;
			DataInputStream inputData = null;
			Socket socket = null;
			Message msg = null;
			String ipHead = getIpHead(localIp);
			
			for(int n=2; n<=254; n++){
				System.out.println("tiger " + ipHead + n);
				try {
					System.out.println("tiger sent start before");
					socket = new Socket();
					socket.connect(new InetSocketAddress(ipHead + n, 6348), 80);
					System.out.println("tiger sent start");

					msg = new Message();
					msg.what = HANDLER_UPDATE_CONNECT;
					msg.obj = socket.getInetAddress().getHostAddress();
					threadMsg.sendMessage(msg);
					
					File file = new File(editText.getText().toString());
					
					fileInput = new FileInputStream(file);
					output = socket.getOutputStream();
					input = socket.getInputStream();
					inputData = new DataInputStream(input);
					
					//file name
					filePackage pack = new filePackage(file.getName(), file.length());
					System.out.println("tiger " + file.getName());
					System.out.println("tiger file length" + file.length());
					
					msg = new Message();
					msg.what = HANDLER_UPDATE_PROGRESSBAR;
					msg.arg1 = (int)file.length();
					threadMsg.sendMessage(msg);
					
					output.write(pack.getBytes());
					System.out.println("tiger sent flag");
					int flag = inputData.readInt();
					if(flag==0)
						System.out.println("tiger receive good");
					
					byte buffer [] = new byte[1024];
					System.out.println("tiger start file sent");
					int cnt = 0, cntSent = 0;
					
					while((cnt = fileInput.read(buffer)) != -1) {
						output.write(buffer, 0 , cnt);
						cntSent += cnt;
						msg = new Message();
						msg.what = HANDLER_UPDATE_PROGRESSSTATE;
						msg.arg1 = cntSent;
						threadMsg.sendMessage(msg);
						//System.out.println("tiger " + cntSent);
					}
					
					output.flush();
					break;
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					System.out.println("tiger sent over1");
					e.printStackTrace();
				} catch (IOException e) {
					System.out.println("tiger connect failed");
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					System.out.println("tiger sent over3");
					try {
						if(input!=null)
							input.close();
						
						if(output!=null)
							output.close();
						
						if(socket!=null)
							socket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			
			msg = new Message();
			msg.what = HANDLER_UPDATE_UI;
			threadMsg.sendMessage(msg);
		}
	}
}

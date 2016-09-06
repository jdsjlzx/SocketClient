package com.android.upload;

import java.io.File;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.android.service.UploadLogService;
import com.android.socket.utils.StreamTool;

/**
 * socket���ļ��ϵ��ϴ��ͷ���
 * 
 * @author dell
 * 
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
@SuppressLint("NewApi")
public class UploadActivity extends Activity {
	private EditText filenameText;
	private TextView resulView;
	private ProgressBar uploadbar;
	private UploadLogService logService;
	private boolean start = true;
	private String path;
	
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			int length = msg.getData().getInt("size");
			uploadbar.setProgress(length);
			float num = (float) uploadbar.getProgress()
					/ (float) uploadbar.getMax();
			int result = (int) (num * 100);
			resulView.setText(result + "%");
			if (uploadbar.getProgress() == uploadbar.getMax()) {
				Toast.makeText(UploadActivity.this, R.string.success, 1).show();
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		logService = new UploadLogService(this);
		filenameText = (EditText) this.findViewById(R.id.filename);
		uploadbar = (ProgressBar) this.findViewById(R.id.uploadbar);
		resulView = (TextView) this.findViewById(R.id.result);
		Button button = (Button) this.findViewById(R.id.button);
		Button select_fileButton = (Button) this
				.findViewById(R.id.id_select_file);
		Button button1 = (Button) this.findViewById(R.id.stop);
		button1.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				start = false;

			}
		});
		select_fileButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent();
				if (Build.VERSION.SDK_INT < 19) {
					intent.setAction(Intent.ACTION_GET_CONTENT);
				} else {
					// ����Intent.ACTION_OPEN_DOCUMENT�İ汾��4.4���ϵ�����
					// ����ͻ�ʹ�õĲ���4.4���ϵİ汾����Ϊǰ�����жϣ����Ը���������else��
					// Ҳ�Ͳ�������κ���Ϊ�����������Ĵ���
					intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
				}
				intent.setType("image/*");//ͼƬ
				//intent.setType("video/*");//��Ƶ
				//intent.setType("audio/amr");//¼��				
				startActivityForResult(intent, 1);
			}
		});
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				start = true;
				String filename = filenameText.getText().toString();
				if (Environment.getExternalStorageState().equals(
						Environment.MEDIA_MOUNTED)) {
					File uploadFile = new File(Environment
							.getExternalStorageDirectory(), filename);
					if (uploadFile.exists()) {
						uploadFile(uploadFile);
					} else {
						Toast.makeText(UploadActivity.this,
								R.string.filenotexsit, 1).show();
					}
				} else {
					Toast.makeText(UploadActivity.this, R.string.sdcarderror, 1)
							.show();
				}
			}
		});
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		super.onActivityResult(requestCode, resultCode, data);
		path = getPathByUri(this, data.getData());
		System.out.println("path:"+path);
		filenameText.setText(path);
		
	}

	/**
	 * �ϴ��ļ�
	 * 
	 * @param uploadFile
	 */
	private void uploadFile(final File uploadFile) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					uploadbar.setMax((int) uploadFile.length());
					String souceid = logService.getBindId(uploadFile);
					String head = "Content-Length=" + uploadFile.length()
							+ ";filename=" + uploadFile.getName()
							+ ";sourceid=" + (souceid == null ? "" : souceid)
							+ "\r\n";
					Socket socket = new Socket("192.168.2.111", 7878);
					OutputStream outStream = socket.getOutputStream();
					outStream.write(head.getBytes());

					PushbackInputStream inStream = new PushbackInputStream(
							socket.getInputStream());
					String response = StreamTool.readLine(inStream);
					String[] items = response.split(";");
					String responseid = items[0].substring(items[0]
							.indexOf("=") + 1);
					String position = items[1].substring(items[1].indexOf("=") + 1);
					if (souceid == null) {// ����ԭ��û���ϴ������ļ��������ݿ����һ���󶨼�¼
						logService.save(responseid, uploadFile);
					}
					RandomAccessFile fileOutStream = new RandomAccessFile(
							uploadFile, "r");
					fileOutStream.seek(Integer.valueOf(position));
					byte[] buffer = new byte[1024];
					int len = -1;
					int length = Integer.valueOf(position);
					while (start && (len = fileOutStream.read(buffer)) != -1) {
						outStream.write(buffer, 0, len);
						length += len;
						Message msg = new Message();
						msg.getData().putInt("size", length);
						handler.sendMessage(msg);
					}
					fileOutStream.close();
					outStream.close();
					inStream.close();
					socket.close();
					if (length == uploadFile.length())
						logService.delete(uploadFile);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}).start();
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	@SuppressLint("NewApi")
	public static String getPathByUri(Context cxt, Uri uri) {
		// �ж��ֻ�ϵͳ�Ƿ���4.4�����ϵ�sdk
		final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
		// �����4.4���ϵ�ϵͳ����ѡ����ļ���4.4ר�е�������ļ�
		if (isKitKat && DocumentsContract.isDocumentUri(cxt, uri)) {
			// ����Ǵ��ⲿ���濨ѡ����ļ�
			if (isExternalStorageDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				if ("primary".equalsIgnoreCase(type)) {
					return Environment.getExternalStorageDirectory() + "/"
							+ split[1];
				}

			}
			// ��������ط��ص�·��
			else if (isDownloadsDocument(uri)) {
				final String id = DocumentsContract.getDocumentId(uri);
				final Uri contentUri = ContentUris.withAppendedId(
						Uri.parse("content://downloads/public_downloads"),
						Long.valueOf(id));

				return getDataColumn(cxt, contentUri, null, null);
			}
			// �����ѡ���ý����ļ�
			else if (isMediaDocument(uri)) {
				final String docId = DocumentsContract.getDocumentId(uri);
				final String[] split = docId.split(":");
				final String type = split[0];

				Uri contentUri = null;
				if ("image".equals(type)) { // ͼƬ
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
				} else if ("video".equals(type)) { // ��Ƶ
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
				} else if ("audio".equals(type)) { // ��Ƶ
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				}

				final String selection = "_id=?";
				final String[] selectionArgs = new String[] { split[1] };

				return getDataColumn(cxt, contentUri, selection, selectionArgs);
			}
		} else if ("content".equalsIgnoreCase(uri.getScheme())) { // ����ǵͶ�4.2���µ��ֻ��ļ�uri��ʽ
			if (isGooglePhotosUri(uri))
				return uri.getLastPathSegment();

			return getDataColumn(cxt, uri, null, null);
		} else if ("file".equalsIgnoreCase(uri.getScheme())) { // �����ͨ��fileת�ɵ�uri�ĸ�ʽ
			return uri.getPath();
		}

		return null;
	}

	public static String getDataColumn(Context context, Uri uri,
			String selection, String[] selectionArgs) {

		Cursor cursor = null;
		final String column = "_data";
		final String[] projection = { column };

		try {
			cursor = context.getContentResolver().query(uri, projection,
					selection, selectionArgs, null);
			if (cursor != null && cursor.moveToFirst()) {
				final int index = cursor.getColumnIndexOrThrow(column);
				return cursor.getString(index);
			}
		} finally {
			if (cursor != null)
				cursor.close();
		}
		return null;
	}

	public static boolean isExternalStorageDocument(Uri uri) {
		return "com.android.externalstorage.documents".equals(uri
				.getAuthority());
	}

	public static boolean isDownloadsDocument(Uri uri) {
		return "com.android.providers.downloads.documents".equals(uri
				.getAuthority());
	}

	public static boolean isMediaDocument(Uri uri) {
		return "com.android.providers.media.documents".equals(uri
				.getAuthority());
	}

	public static boolean isGooglePhotosUri(Uri uri) {
		return "com.google.android.apps.photos.content".equals(uri
				.getAuthority());
	}

}
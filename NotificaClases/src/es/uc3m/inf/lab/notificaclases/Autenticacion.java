package es.uc3m.inf.lab.notificaclases;

import java.util.List;

import javax.net.SocketFactory;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.util.ssl.SSLUtil;
import com.unboundid.util.ssl.TrustAllTrustManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Actividad donde se gestiona el inicio de sesión a través de LDAP.
 * @author Rafael
 *
 */
public class Autenticacion extends Activity {

	private Handler mHandler;
	private SharedPreferences preferencias;
	private SharedPreferences.Editor editor;
	private EditText user, pass, passLDAP;

	@SuppressLint("HandlerLeak")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.autenticacion);

		//Se prepara el archivo de preferencias para su acceso y edición
		preferencias = getSharedPreferences("ARCHIVO_PREFERENCIAS" , Context.MODE_PRIVATE);
		editor = preferencias.edit();

		//Se rellena el campo usuario con los datos guardados en el archivo de preferencias o en su defecto se deja vacio
		String usuario = preferencias.getString("usuario", "");
		user = (EditText) findViewById(R.id.editText_usuario);
		user.setText(usuario);
		
		pass = (EditText) findViewById(R.id.editText_password);
		passLDAP = (EditText) findViewById(R.id.editText_passLDAP);

		//Se determina el comportamiento del manejador usado para recuperar mensajes durante el intento de conexión al servidor LDAP
		mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				Toast.makeText(Autenticacion.this, msg.obj.toString(),Toast.LENGTH_SHORT).show();
				if(msg.arg1==1)
					cambiarPantalla();
			}
		};
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

	/** Called when the user clicks the Connect button */
	public void conectar(View view) { // Do something in response to button

		String usuario = user.getText().toString();
		String password = pass.getText().toString();
		String passwordLDAP = passLDAP.getText().toString();

		//Se comprueba que ningun campo esta vacio
		if(usuario.isEmpty() | password.isEmpty() | passwordLDAP.isEmpty()){
			Toast.makeText(Autenticacion.this, "Rellene todos los datos", Toast.LENGTH_SHORT).show();
		}else{
			//Se guarda el usuario en el archivo de preferencias
			editor.putString("usuario", usuario);
			editor.commit();

			conectarConLDAP(usuario, password, passwordLDAP);
		}
	}

	private void conectarConLDAP(final String usuario, final String password,final String connectPW){
		final Message msg = new Message();
		msg.arg1=0;

		Thread t = new Thread(){
			public void run(){

				// Datos de conexión
				String host;
				final int port = 636;
				String baseDN;
				String connectDN = "uid=consultas,dc=lab,dc=inf,dc=uc3m,dc=es";
				host = "163.117.142.173"; //ldap.lab.inf.uc3m.es
				baseDN = "ou=people,dc=lab,dc=inf,dc=uc3m,dc=es";
				

				SocketFactory socketFactory = null;
				final SSLUtil sslUtil = new SSLUtil(new TrustAllTrustManager());
				try {
					socketFactory = sslUtil.createSSLSocketFactory();
				} catch (Exception e) {
					msg.obj = "Cannot initialize SSL -- " + e.getMessage();
					Log.e("MainActivity/Thread t", (String) msg.obj);
					mHandler.sendMessage(msg);
					SystemClock.sleep(1000);
				}

				// Opciones de conexión
				final LDAPConnectionOptions opciones = new LDAPConnectionOptions();
				opciones.setAutoReconnect(true);
				opciones.setConnectTimeoutMillis(30000);
				opciones.setFollowReferrals(false);
				opciones.setMaxMessageSize(1024*1024);

				LDAPConnection connection = null;
				try {

					connection = new LDAPConnection(socketFactory, opciones, host, port, connectDN, connectPW);

					// Búsqueda dentro del LDAP
					SearchResult sr = connection.search(baseDN, SearchScope.SUB, "uid="+usuario);
					List<SearchResultEntry> list = sr.getSearchEntries();
					String user_completo = "";
					if(list.size()>0)
						user_completo = list.get(0).getDN();

					connection.bind(user_completo, password);

					msg.obj = "Inicio de sesión correcto";
					msg.arg1=1;
					mHandler.sendMessage(msg);
					SystemClock.sleep(1000);

					connection.close();

				} catch (LDAPException e) {
					msg.obj = "Error --> " + e.getMessage();
					Log.e("MainActivity/Thread t", (String) msg.obj);
					mHandler.sendMessage(msg);
					if(connection != null){
						SystemClock.sleep(3000);
						connection.close();
					}
				}

			}
		};
		t.start();
	}

	private void cambiarPantalla(){
		//Se cambia a la InitServiceActivity
		Intent intent = new Intent(this, Principal.class);
		startActivity(intent);
	}

}

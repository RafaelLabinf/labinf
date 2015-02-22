package es.uc3m.inf.lab.notificaclases;

import java.util.ArrayList;

import es.uc3m.inf.lab.notificaclases.Servicio.LocalBinder;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

/**
 * Actividad que inicia o para el servicio y muestra la información obtenida de la BBDD
 * @author Rafael
 *
 */
public class Principal extends Activity {

	private Intent i_service;
	private Servicio servicio;
	private boolean servicioConectado = false;
	private BroadcastReceiver receiver;
	private TextView textview_contenido;
	private final ServiceConnection mConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			servicioConectado = true;
			LocalBinder mLocalBinder = (LocalBinder)service;
			servicio = mLocalBinder.getService();
			Log.i("InitServiceActivity", "onServiceConnected");
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			servicioConectado = false;
			servicio=null;
			Log.i("InitServiceActivity", "onServiceDisconnected");
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.principal);

		// Create and register broadcast receiver
		IntentFilter filtro = new IntentFilter();
		filtro.addAction("es.uc3m.inf.lab.notificaclases.CONTENIDO_LISTO");
		receiver = new BroadcastReceiver() {
			@SuppressWarnings("unchecked")
			@Override
			public void onReceive(Context context, Intent intent) {
				textview_contenido.setText("");
				ArrayList<String> contenido = (ArrayList<String>) intent.getSerializableExtra("contenido");
				for(int i=0; i<contenido.size(); i++){
					textview_contenido.setText(textview_contenido.getText() + contenido.get(i) + "\n");
					//Log.i("InitServiceActivity", "BroadcastReceiver: " + contenido.get(i));
				}
			}
		};
		// Registramos el BroadcastReceiver para que filtre el IntentFilter
		registerReceiver(receiver, filtro);

		//Se obtiene el textview_contenido
		textview_contenido = (TextView) findViewById(R.id.textview_contenido);
		
		//Inicializamos el servicio
		this.i_service = new Intent (this, Servicio.class);
		startService(this.i_service);
		bindService(i_service, mConnection, BIND_AUTO_CREATE);
		textview_contenido.setText("");
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.init, menu);
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
	protected void onStop() { //Cuando se deja la app en bg
		super.onStop();
		Log.i("InitServiceActivity","onStop");
	}

	@Override
	protected void onRestart() { //Cuando se pasa la app de bg a fg
		super.onRestart();
		if(servicioConectado)
			servicio.conectarBDD();
		Log.i("InitServiceActivity","onRestart");
	}

	@Override
	protected void onDestroy() { //Cuando se cierra la app

		super.onDestroy();

		//Se "apaga" el servicio si está activado
		if(servicioConectado){
			unbindService(mConnection);
			servicio = null;
			stopService(this.i_service);
			servicioConectado = false;
		}

		//Se "apaga" también el BroadcastReceiver
		unregisterReceiver(receiver);

		Log.i("InitServiceActivity","onDestroy");

	}

	/** Called when the user clicks the "button_iniciar_service" */
	public void initService(View view) {

		if(!servicioConectado){
			this.i_service = new Intent (this, Servicio.class);
			startService(this.i_service);
			bindService(i_service, mConnection, BIND_AUTO_CREATE);
			//servicioConectado = true;
		}

		textview_contenido.setText("");

		Log.i("InitServiceActivity", "initService");

	}

	/** Called when the user clicks the "button_parar_service" */
	public void stopService(View view) {

		if(servicioConectado){
			unbindService(mConnection);
			servicio = null;
			stopService(this.i_service);
			servicioConectado = false;
		}

		textview_contenido.setText("¡Inicie el servicio para poder ver la siguiente clase!");

		Log.i("InitServiceActivity","stopService");

	}

}

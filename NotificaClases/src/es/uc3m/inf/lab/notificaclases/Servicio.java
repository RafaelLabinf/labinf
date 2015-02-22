package es.uc3m.inf.lab.notificaclases;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Calendar;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import es.uc3m.inf.lab.notificaclases.utilidades.MySSLSocketFactory;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

public class Servicio extends Service{

	//Variables globales de configuracion
	private final String SERVIDOR = "163.117.170.62"; // IP o dominio ("reservas.lab.inf.uc3m.es")
	private final String SERVICIO_WEB = "consulta_reservas.php";
	private final String USER_BD = "consultas";
	private final int TIEMPO_CONSULTA = 30; //Cada cuanto se hace una consulta (en minutos)
	private final int TIEMPO_NOTIFICACION = 5; //minutos antes de que salte la notificacion

	private final IBinder binder = new LocalBinder();
	private NotificationManager not_man;
	private Calendar currentDate;
	private ArrayList<String> aulas;
	private int ms_prox_clase = 86400000; //se inicializa a 24h para que si no hay clases no salte notificacion
	private Handler handlerConexionBDD;
	private Runnable runnableConexionBDD = new Runnable() {
		@Override
		public void run() {
			// Nos conectamos a la BDD cada "TIEMPO_CONSULTA" minutos.
			conectarBDD();
			handlerConexionBDD.postDelayed(this, TIEMPO_CONSULTA*60000);
		}
	};
	private Handler handlerNotificaciones;
	private Runnable runnableNotificaciones = new Runnable() {
		@Override
		public void run() {
			for(int i=0; i<aulas.size(); i++)
				lanzarNotificacion(i, aulas.get(i));
		}
	};
	private Handler handlerRefrescar;
	private Runnable runnableRefrescar = new Runnable() {
		@Override
		public void run() {
			conectarBDD();
		}
	};


	public class LocalBinder extends Binder{
		Servicio getService(){
			return Servicio.this;
		}
	}

	public void onCreate(){
		this.not_man = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		//Iniciamos el handler de modo que se conecte a la BDD y vuelva a hacerlo cada media hora
		handlerConexionBDD = new Handler();
		handlerConexionBDD.postDelayed(runnableConexionBDD, 0);

		Toast.makeText(this, "NotificaClases: Servicio iniciado", Toast.LENGTH_SHORT).show();
	}

	public void onDestroy(){
		for(int i=0; i<aulas.size(); i++)
			this.not_man.cancel(i);
		//Se para la conexión con la BDD cada media hora
		handlerConexionBDD.removeCallbacks(runnableConexionBDD);
		Toast.makeText(this, "NotificaClases: Servicio detenido", Toast.LENGTH_SHORT).show();
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Return the communication channel to the service
		return binder;
	}

	public void conectarBDD(){

		//Conseguimos la fecha de hoy, el día de la semana, la hora actual y el cuatrimestre
		currentDate = Calendar.getInstance();
		//Log.i("conectarBDD", currentDate.toString());
		int dia_actual = currentDate.get(Calendar.DAY_OF_MONTH);
		String dia = Integer.toString(dia_actual);
		int mes_actual = currentDate.get(Calendar.MONTH)+1;//Los meses empiezan en 0
		String mes = Integer.toString(mes_actual);
		if(dia_actual<10)
			dia = "0" + dia;
		if(mes_actual<10)
			mes = "0" + mes;
		String fechaDeHoy = currentDate.get(Calendar.YEAR) + "-" + mes + "-" + dia;

		int day_of_week = currentDate.get(Calendar.DAY_OF_WEEK);
		String dia_de_la_semana = "";
		switch(day_of_week){
		case 2: dia_de_la_semana = "Lunes";
		break;
		case 3: dia_de_la_semana = "Martes";
		break;
		case 4: dia_de_la_semana = "Miercoles";
		break;
		case 5: dia_de_la_semana = "Jueves";
		break;
		case 6: dia_de_la_semana = "Viernes";
		break;
		default: dia_de_la_semana = "Fin de semana";
		break;
		}

		String hora = currentDate.get(Calendar.HOUR_OF_DAY) + ":" + currentDate.get(Calendar.MINUTE) + ":00";

		int cuatri = 1;
		if (mes_actual>=1 && mes_actual<=6)
			cuatri = 2;
		else if (mes_actual>=7 && mes_actual<=8)
			cuatri = 0;

		// Conectamos con la base de datos dando como parámetros los datos que se acaban de conseguir
		new PHPCallTask().execute(dia_de_la_semana, fechaDeHoy, Integer.toString(cuatri), hora);

	}

	// La conexión con la base de datos es obligatorio hacerla en una tarea asincrona
	private class PHPCallTask extends AsyncTask<String, Void, ArrayList<String>> {

		protected ArrayList<String> doInBackground(String... datos) {

			//Se asignan los valores que el servidor leerá en la variable $_REQUEST['clave']
			ArrayList<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("user", USER_BD));
			nameValuePairs.add(new BasicNameValuePair("password", ""));
			nameValuePairs.add(new BasicNameValuePair("dia", datos[0]));
			nameValuePairs.add(new BasicNameValuePair("fecha", datos[1]));
			nameValuePairs.add(new BasicNameValuePair("cuatrimestre", datos[2]));
			nameValuePairs.add(new BasicNameValuePair("hora_actual", datos[3]));

			//https post
			InputStream is = null;
			try{

				HttpClient httpclient = getNewHttpClient();
				HttpPost httppost = new HttpPost("https://" + SERVIDOR + "/" + SERVICIO_WEB);
				httppost.setEntity((HttpEntity) new UrlEncodedFormEntity(nameValuePairs));
				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();
				is = entity.getContent();

			}catch(Exception e){
				Log.e("PHPCallTask", "Error in http connection "+e.toString());
			}

			//convert response to string
			String result = "";
			try{
				BufferedReader reader = new BufferedReader(new InputStreamReader(is,"utf8"),8);
				StringBuilder sb = new StringBuilder();
				String line = null;
				while ((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
				is.close();

				result=sb.toString();
			}catch(Exception e){
				Log.e("PHPCallTask", "Error converting result "+e.toString());
			}

			ArrayList<String> proximas_clases = new ArrayList<String>();
			aulas = new ArrayList<String>();

			try {

				JSONArray resultJSONArray;
				if(result.equals("null\n")){
					resultJSONArray = new JSONArray();
					proximas_clases.add("No hay más clases hoy.");
				}else{
					resultJSONArray = new JSONArray(result);
					boolean seguir;
					int num_clase = 0;
					String hora_clase_actual;
					do{
						JSONObject json_data = resultJSONArray.getJSONObject(num_clase);
						proximas_clases.add("-A las " + json_data.getString("hora_inicio") + 
								" empieza una clase en el aula " + json_data.getString("aula") + 
								" de " + json_data.getString("asignatura") + 
								" (grupo " + json_data.getInt("grupo") + 
								") con el profesor " + json_data.getString("profesor") + ".");
						aulas.add(json_data.getString("aula"));

						hora_clase_actual = json_data.getString("hora_inicio");
						//Log.i("PHPCallTask", "hora_clase_actual = " + hora_clase_actual.substring(0,2));

						if(resultJSONArray.length()-num_clase > 1){ //Si queda más de un JSONObject por leer en el JSONArray
							String hora_clase_siguiente = resultJSONArray.getJSONObject(num_clase+1).getString("hora_inicio");
							if(hora_clase_actual.equals(hora_clase_siguiente))
								seguir = true;
							else
								seguir = false;
						}else
							seguir = false;
						num_clase++;
					}while(seguir);

					ms_prox_clase = Integer.parseInt(hora_clase_actual.substring(0,2))*3600000;

				}

			} catch (JSONException e) {
				Log.e("PHPCallTask", "Error parsing data "+e.toString());
			}

			return proximas_clases;
		}

		//Método para obtener un cliente HTTPS
		public HttpClient getNewHttpClient() {
			try {
				KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
				trustStore.load(null, null);

				SSLSocketFactory sf = new MySSLSocketFactory(trustStore);
				sf.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

				HttpParams params = new BasicHttpParams();
				HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
				HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);

				SchemeRegistry registry = new SchemeRegistry();
				registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
				registry.register(new Scheme("https", sf, 443));

				ClientConnectionManager ccm = new ThreadSafeClientConnManager(params, registry);

				return new DefaultHttpClient(ccm, params);
			} catch (Exception e) {
				return new DefaultHttpClient();
			}
		}

		protected void onPostExecute(ArrayList<String> contenido) {
			//Se mandan las próximas clases por broadcast para que las muestre InitServiceActivity
			Intent broadcast = new Intent("es.uc3m.inf.lab.notificaclases.CONTENIDO_LISTO");
			broadcast.putExtra("contenido", contenido);
			broadcast.setPackage("es.uc3m.inf.lab.notificaclases");
			Context context = getApplicationContext();
			context.sendBroadcast(broadcast);

			//Se crea la siguiente notificación
			setNotificationTime();
		}

	}

	private void setNotificationTime(){

		int ms_actuales = currentDate.get(Calendar.HOUR_OF_DAY)*3600000 +
				currentDate.get(Calendar.MINUTE)*60000 +
				currentDate.get(Calendar.SECOND)*1000 +
				currentDate.get(Calendar.MILLISECOND);

		int ms_prox_notificacion = ms_prox_clase - TIEMPO_NOTIFICACION*60000 - ms_actuales;

		//Log.i("setNotificationTime", "ms_prox_notificacion = " + ms_prox_notificacion);

		//Se inicia el handler de notificaciones de modo que se lance la próxima notificación TIEMPO_NOTIFICACION minutos antes que la clase
		handlerNotificaciones = new Handler();
		handlerNotificaciones.postDelayed(runnableNotificaciones, ms_prox_notificacion);

		//Se inicia el handler de refresco de modo que cuando se pase la hora de la próxima clase se refresque la pantalla
		handlerRefrescar = new Handler();
		handlerRefrescar.postDelayed(runnableRefrescar, ms_prox_clase - ms_actuales + 1000);

	}

	private void lanzarNotificacion(int IDNotificacion, String clase){
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentTitle("NotificaClases!")
		.setContentText(clase);

		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = new Intent(this, Principal.class);

		// The stack builder object will contain an artificial back stack for the
		// started Activity.
		// This ensures that navigating backward from the Activity leads out of
		// your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(Principal.class);
		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);

		// IDNotificacion allows you to update the notification later on.
		this.not_man.notify(IDNotificacion, mBuilder.build());
	}

}

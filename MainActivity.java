kage com.estimote.configuration;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.estimote.sdk.EstimoteSDK;
import com.estimote.sdk.SystemRequirementsChecker;
import com.estimote.sdk.cloud.EstimoteCloud;
import com.estimote.sdk.connection.scanner.ConfigurableDevice;
import com.estimote.sdk.connection.scanner.ConfigurableDevicesScanner;
import com.estimote.sdk.repackaged.gson_v2_3_1.com.google.gson.ExclusionStrategy;
import com.estimote.sdk.telemetry.EstimoteTelemetry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_SCAN_RESULT_ITEM_DEVICE = "com.estimote.configuration.SCAN_RESULT_ITEM_DEVICE";

    private static final int REQUEST_ENABLE_BT = 0;
    //private static final int REQUEST_DISCOVER_BT = 1;

    ConfigurableDevicesScanner devicesScanner;

    String buffMuestras = "";
    public  String      fileName = "";
    private TCPClient   mTcpClient;
    public  TextView    devicesCountTextView;
    public  TextView    deviceData;
    public  Button      BtnSave, BtnLoad, btnIniciar;
    public  TextView    textArchivo, cRecord, distanciaTextView;
    public  EditText    TxtMuestras, mDistancia;

    public boolean record = false;
    public int countRecord = 0;
    public int muestras = 0;
    public int distancia = 0;
    public int potenciaRx = 0;
    BluetoothAdapter mBlueAdapter;

    public String path = Environment.getExternalStorageDirectory().getAbsolutePath()+ "/Caracterización Beacons";

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddhhmmss", Locale.getDefault());
    Date date = new Date();
    final String fecha = dateFormat.format(date);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        BtnSave              = (Button)   findViewById(R.id.BtnSave );
        BtnLoad              = (Button)   findViewById(R.id.BtnLoad );
        textArchivo          = (TextView) findViewById( R.id.txtViewValores ) ;
        deviceData           = (TextView) findViewById(R.id.text);
        devicesCountTextView = (TextView) findViewById(R.id.devices_count);
        TxtMuestras          = (EditText) findViewById( R.id.editTextDistancia );
        btnIniciar           = (Button)   findViewById( R.id.BtnIniciar );
        mBlueAdapter         =            BluetoothAdapter.getDefaultAdapter();
        cRecord              = (TextView) findViewById(R.id.cRecord);
        mDistancia           = (EditText) findViewById( R.id.mDistancia );
        distanciaTextView    = (TextView) findViewById( R.id.distanciaTextView ) ;
        devicesScanner = new ConfigurableDevicesScanner(this);
        devicesScanner.setScanPeriodMillis(1000);


        //Crear Directorio
        File dir =  new File( path );
        dir.mkdirs();

        // Conectarse al server
        new ConnectTask().execute("");

        //encender Blouetooth
        if (!mBlueAdapter.isEnabled()){
            showToast("Encendiendo Bluetooth...");
            //intent para encender bluetooth
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_ENABLE_BT);
        }
        else{
            showToast("Bluetooth ya está activo");
        }


        showToast( "Asegurese de ingresar un valor en metros distinto de cero" );




        devicesScanner.scanForDevices( new ConfigurableDevicesScanner.ScannerCallback() {
            @Override
            public void onDevicesFound(List<ConfigurableDevicesScanner.ScanResultItem> list) {
                devicesCountTextView.setText( getString( R.string.detected_devices ) + ": " + String.valueOf( list.size() ) );

                SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyyMMddhhmmss", Locale.getDefault() );
                Date date = new Date();
                final String fecha = dateFormat.format( date );

                //SECCION DE CODIGOS PARA CALCULOS DE FUNCIONES

                /*Declaracion de variables*/
                Double A;
                Double B;
                Double C;
                potenciaRx = Integer.parseInt(mDistancia.getText().toString());


                String data = "";
                String data2 = "";
                if (!list.isEmpty()) {
                    if(record){
                        for (int i = 0; i < list.size(); i++) {
                            ConfigurableDevicesScanner.ScanResultItem item = list.get( i );
                            distancia = item.rssi.intValue();

                            /*funcion logaritmica*/
                            A = (-7.162)*Math.log( potenciaRx )-70.378;
                            /*despeje de la funcion logaritmica para distancia*/
                            B = Math.exp( (70.378+distancia)/-7.162 );
                            /*despeje de la funcion logaritmica para distancia funcion de muestreo dia 2*/
                            C = Math.exp( (76.05+distancia)/-6.391 );


                            int numero = 4 * (distancia * (-1));
                            data = data + item.txPower.toString() + "," +
                                    item.rssi.toString() + "," +
                                    item.device.macAddress.toString() + "," +
                                    "200" + "," +
                                    numero + "," +
                                    mDistancia.getText().toString() + "," +
                                    A + "," +
                                    B + "," +
                                    C + "," +
                                    fecha + "\n";

                            data2 = data2 + item.rssi.toString() + ";" + "\n" + B + ";" + "\n" + C + "\n";
                        }
                        buffMuestras=buffMuestras+data;
                        textArchivo.setText( buffMuestras );
                        distanciaTextView.setText( data2 );
                        if(countRecord==muestras){
                            showToast("Muestreo culminado");
                            stopRecord();
                            cRecord.setText( getString( R.string.nro_de_registro ) +":"+ "0");
                            countRecord = 0;
                        }else{
                            countRecord++;
                            cRecord.setText( getString( R.string.nro_de_registro ) +": "+ String.valueOf(countRecord));
                        }
                    }

                    /*String message = deviceData.getText().toString();
                    //sends the message to the server
                    if (mTcpClient != null) {
                        mTcpClient.sendMessage( message );
                    }*/
                }
            }
        } );
    }


    public void startRecord(View view){
        muestras = Integer.parseInt(TxtMuestras.getText().toString());
        record=true;
        cRecord.setText("0");
        textArchivo.setText("");
        buffMuestras="";
        showToast("Se inició la grabación. muestras: "+muestras+", countRecord:"+countRecord);
    }

    public void stopRecord(){
        showToast("Se detuvo la grabación");
        record=false;
    }

    public  void ButtonSave (View view) {


        showToast( fecha );

        File file = new File( path +"/"+ fecha+ ".txt" );
        String [] saveText = String.valueOf( textArchivo.getText() ).split( System.getProperty( "line.separator" ) );


        Toast.makeText( getApplicationContext(), "saved Text", Toast.LENGTH_SHORT ).show();

        Save (file,saveText);


    }

    public void ButtonLoad (View view){

        File file = new File( path +"/"+ fecha+ ".txt" );
        String [] loadText =  Load(file);
        String finalString = "";

        for (int i = 0;  i < loadText.length; i++){

            finalString += loadText[i] + System.getProperty( "line.separator" );

        }

        textArchivo.setText( finalString );


    }

    public static void Save(File file, String [] data){

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream( file );
        }catch (FileNotFoundException e) {e.printStackTrace();}

        try {
            try {
                for (int i = 0; i<data.length; i++){

                    fos.write( data[i].getBytes() );
                    if (i<data.length-1){
                        fos.write( "\n".getBytes() );
                    }
                }
            }catch (IOException e) {e.printStackTrace();}
        }
        finally {
            try {
                fos.close();
            }catch (IOException e) {e.printStackTrace();}
        }
    }

    public static String [] Load(File file){

        FileInputStream fis= null;
        try {
            fis = new FileInputStream( file );
        }catch (FileNotFoundException e) {e.printStackTrace();}
        InputStreamReader isr = new InputStreamReader( fis );
        BufferedReader br = new BufferedReader( isr );

        String test;
        int varfile = 0;
        try {
            while ((test=br.readLine()) != null){varfile++;}
        }catch (IOException e) {e.printStackTrace();}

        try {
            fis.getChannel().position(0);
        }catch (IOException e) {e.printStackTrace();}

        String[] array = new  String[varfile];
        String line;
        int i = 0;
        try {
            while ((line=br.readLine()) !=null)
            {array[i] = line;
                i++;}
        }catch (IOException e) {e.printStackTrace();}
        return array;



    }

    @Override
    protected void onResume() {
        super.onResume();

        devicesScanner.isScanning();


    }

    @Override
    protected void onPause() {
        super.onPause();

        devicesScanner.stopScanning();

        super.onPause();

        // disconnect TCP client
        mTcpClient.stopClient();
        mTcpClient = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode){
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK){
                    //Bluetooth esta encendido
                    showToast("Bluetooth está Encendido");
                }
                else {
                    //usuario denegó encender bluetooth
                    showToast("No se pudo encender Bluetooth");
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void showToast(String msg) { Toast.makeText( this, msg, Toast.LENGTH_SHORT).show();}


    public class ConnectTask extends AsyncTask<String, String, TCPClient> {

        @Override
        protected TCPClient doInBackground(String... message) {

            //we create a TCPClient object and
            mTcpClient = new TCPClient(new TCPClient.OnMessageReceived() {
                @Override
                //here the messageReceived method is implemented
                public void messageReceived(String message) {
                    //this method calls the onProgressUpdate
                    publishProgress(message);
                }
            });
            mTcpClient.run();

            return null;
        }

    }



}

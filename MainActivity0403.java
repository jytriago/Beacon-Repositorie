package com.estimote.configuration;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import java.text.DecimalFormat;
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
    String condicionDmuestra = "";
    private TCPClient   mTcpClient;
    public  TextView    devicesCountTextView, cRecord;
    public  ImageButton btnIniciar, btnParar;
    public  EditText    IpClient, PuertoClient, condicionMuestra;

    public boolean record = false;
    public int countRecord = 0;
    BluetoothAdapter mBlueAdapter;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mBlueAdapter         = BluetoothAdapter.getDefaultAdapter();
        btnIniciar           = findViewById( R.id.BtnIniciar );
        btnParar             = findViewById( R.id.BtnParar );
        devicesCountTextView = findViewById(R.id.devices_count);
        cRecord              = findViewById(R.id.cRecord);
        IpClient             = findViewById( R.id.editTextIP );
        PuertoClient         = findViewById( R.id.editTextPuerto );
        condicionMuestra     = findViewById( R.id.consideracionTxtView );

        devicesScanner = new ConfigurableDevicesScanner(this);

        devicesScanner.setScanPeriodMillis(1000);


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


        devicesScanner.scanForDevices( new ConfigurableDevicesScanner.ScannerCallback() {
            @Override
            public void onDevicesFound(List<ConfigurableDevicesScanner.ScanResultItem> list) {
                devicesCountTextView.setText( getString( R.string.detected_devices ) + ": " + String.valueOf( list.size() ) );

                SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyyMMddhhmmss", Locale.getDefault() );
                Date date = new Date();
                final String fecha = dateFormat.format( date );


                //DecimalFormat formateador = new DecimalFormat("#.###");

                String data = "";
                if (!list.isEmpty()) {
                    if(record){
                        for (int i = 0; i < list.size(); i++) {
                            ConfigurableDevicesScanner.ScanResultItem item = list.get( i );

                            data = data +
                                    item.rssi.toString() + "," +
                                    item.device.macAddress.toString() + "," +
                                    fecha + "," +
                                    condicionMuestra.getText().toString() +"\n";

                        }

                        buffMuestras=buffMuestras+data;
                        countRecord++;
                        cRecord.setText( getString( R.string.nro_de_registro ) +": "+ String.valueOf(countRecord));

                    }

                    String message = buffMuestras;
                    //sends the message to the server
                    if (mTcpClient != null) {
                        mTcpClient.sendMessage( message );
                    }
                }
            }
        } );
    }


    public void startRecord(View view){
        record=true;
        cRecord.setText("0");
        buffMuestras="";
        showToast("Se inició la grabación. countRecord:"+countRecord);
    }

    public void stopRecord(View view){
        showToast("Se detuvo la grabación");
        record=false;
        cRecord.setText( getString( R.string.nro_de_registro ) +":"+ "0");
        countRecord = 0;
        showToast("Muestreo culminado:"+ countRecord);
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
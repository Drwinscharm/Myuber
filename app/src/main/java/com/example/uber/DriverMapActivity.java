package com.example.uber;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.internal.ConnectionCallbacks;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,RoutingListener {


    private GoogleMap mMap;

    Location mLastLocation;
    LocationRequest mLocationRequest;

    private FusedLocationProviderClient mFusedLocationClient;

    private Button mLogout, mSettings, mRideStatus;

    private Switch mworkingSwitch;

    private int status = 0;

    private String customerId = "", destination;

    private LatLng destinationLatLng, pickupLatLng;
    private float rideDistance;

    private Boolean isLoggingOut = false;

    private SupportMapFragment mapFragment;

    private LinearLayout mCustomerInfo;

    private ImageView mCustomerProfileImage;

    private TextView mCustomerName, mCustomerPhone, mCustomerDestination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        polylines = new ArrayList<>();

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

        mCustomerInfo = (LinearLayout) findViewById(R.id.customerInfo);

        mCustomerProfileImage = (ImageView) findViewById(R.id.customerProfileImage);

        mCustomerName = (TextView) findViewById(R.id.customerName);
        mCustomerPhone = (TextView) findViewById(R.id.customerPhone);
        mCustomerDestination = (TextView) findViewById(R.id.customerDestination);

        mRideStatus  = (Button) findViewById(R.id.rideStatus);
        mSettings = (Button) findViewById(R.id.settings);
        mLogout = (Button) findViewById(R.id.logout);

        mworkingSwitch = (Switch) findViewById(R.id.workingSwitch);
        mworkingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked){
                    connectDriver();
                }else{
                    disconnectDriver();
                }
            }
        });

        mRideStatus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (status){
                    case 1: //if the status is 1, the driver is on its way to pickup the customer
                        status = 2;
                        erasePolylines();
                        if(destinationLatLng.latitude != 0.0 && destinationLatLng.longitude !=0.0) {
                            destinationMarker = mMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination"));
                            getRouteToMarker(destinationLatLng);
                        }
                        pickupMarker.remove();
                        mRideStatus.setText("Drive Completed");
                        break;
                    case 2: //If the status is 2, the driver is on the way to the destination with the customer
                        recordRide();
                        endRide();
                        break;
                }
            }
        });
        mLogout.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                isLoggingOut = true;
                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        getAssignedCustomer();
    }


    private void getAssignedCustomer(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("CustomerRequest").child("CustomerRideId");
        assignedCustomerRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    status = 1;
                    customerId = snapshot.getValue().toString();
                    getAssignedCustomerPickupLocation();
                    getAssignedCustomerDestination();
                    getAssignedCustomerInfo();

                }else{
                    endRide();

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    Marker pickupMarker;
    Marker destinationMarker;
    private DatabaseReference assignedCustomerPickupLocationRef;
    private ValueEventListener assignedCustomerPickupLocationRefListener;
    private void getAssignedCustomerPickupLocation(){
        assignedCustomerPickupLocationRef = FirebaseDatabase.getInstance().getReference().child("CustomerRequest").child(customerId).child("l");
        assignedCustomerPickupLocationRefListener = assignedCustomerPickupLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && !customerId.equals("")){
                    List<Object> map = (List<Object>) snapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;

                    if(map.get(0) !=null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) !=null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    pickupLatLng = new LatLng(locationLat,locationLng);
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pick up Location"));
                    getRouteToMarker(pickupLatLng);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getRouteToMarker(LatLng pickupLatLng) {
        Routing routing = new Routing.Builder()
                .key("AIzaSyCSPuyrfZRp5C6TICvj6FkySjoVtuLiOfE")
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLng)
                .build();
        routing.execute();
    }

    private void getAssignedCustomerDestination(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignedCustomerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("CustomerRequest");
        assignedCustomerRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if(map.get("Destination")!=null){
                        destination = map.get("Destination").toString();
                        mCustomerDestination.setText("Destination:"+ destination);
                    }
                    else{
                        mCustomerDestination.setText("Destination: -- " );

                    }

                    Double destinationLat = 0.0;
                    Double destinationLng = 0.0;
                    if(map.get("DestinationLat") != null){
                        destinationLat = Double.valueOf(map.get("DestinationLat").toString());
                    }
                    if(map.get("DestinationLng") != null){
                        destinationLng = Double.valueOf(map.get("DestinationLng").toString());
                        destinationLatLng = new LatLng(destinationLat, destinationLng);
                    }
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    private void getAssignedCustomerInfo(){

        DatabaseReference mCustomerDataBase = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId);
        mCustomerDataBase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && snapshot.getChildrenCount()>0){
                    mCustomerInfo.setVisibility(View.VISIBLE);
                    Map<String, Object > map = (Map<String, Object>) snapshot.getValue();
                    if (map.get("name") != null){
                        mCustomerName.setText( map.get("name").toString());
                    }
                    if (map.get("phone") != null){
                        mCustomerPhone.setText( map.get("phone").toString());
                    }
                    if (map.get("profileImageUrl") != null){
                        Glide.with(getApplicationContext()).load(map.get("profileImageUrl").toString()).into(mCustomerProfileImage);

                    }

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void endRide(){
        mRideStatus.setText("Picked Customer");
        erasePolylines();
        rideDistance = 0;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("CustomerRequest");
        driverRef.removeValue();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("CustomerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(customerId);

        customerId = "";



        if(pickupMarker !=null) {
            pickupMarker.remove();

        }

        if(destinationMarker !=null) {
            destinationMarker.remove();

        }
        if(assignedCustomerPickupLocationRefListener != null) {
            assignedCustomerPickupLocationRef.removeEventListener(assignedCustomerPickupLocationRefListener);
        }
        mCustomerInfo.setVisibility(View.GONE);
        mCustomerProfileImage.setImageResource(R.mipmap.ic_person);
        mCustomerDestination.setText("Destination: -- " );
    }

     private void recordRide(){
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(userId).child("History");
            DatabaseReference customerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Customers").child(customerId).child("History");
            DatabaseReference historyRef = FirebaseDatabase.getInstance().getReference().child("Users").child("History");
            String requestId = historyRef.push().getKey();
            driverRef.child(requestId).setValue(true);
            customerRef.child(requestId).setValue(true);

            HashMap map = new HashMap();
            map.put("Driver", userId);
            map.put("Customer", customerId);
            map.put("rating", 0);
            map.put("timestamp", getCurrentTimestamp());
            map.put("desination", destination);
            map.put("location/from/lat",pickupLatLng.latitude);
            map.put("location/from/lng",pickupLatLng.longitude);
            map.put("location/to/lat",destinationLatLng.latitude );
            map.put("location/to/lng",destinationLatLng.longitude );
            map.put("distance",rideDistance );
            historyRef.child(requestId).updateChildren(map);


        }

    private Long getCurrentTimestamp() {
        Long timestamp = System.currentTimeMillis()/1000;
        return timestamp;
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }

    }

    LocationCallback mLocationCallBack = new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for (Location location : locationResult.getLocations() ){
                if(getApplicationContext()!=null){

                    if(!customerId.equals("")){
                        rideDistance += mLastLocation.distanceTo(location)/1000;
                    }
                    mLastLocation = location;
                    LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("DriversAvailable");
                    DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("DriversWorking");
                    GeoFire geoFireAvailable = new GeoFire(refAvailable);
                    GeoFire geoFireWorking = new GeoFire(refWorking);
                    switch (customerId){
                        case "":
                            geoFireWorking.removeLocation(userId);
                            geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                            break;

                        default:
                            geoFireAvailable.removeLocation(userId);
                            geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude()));
                            break;
                    }

                }

            }
        }
    };



    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)){
                new AlertDialog.Builder(this).setTitle("give permission").setMessage("give permission message").setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                    }
                })
                        .create()
                        .show();

            }else{
                ActivityCompat.requestPermissions(DriverMapActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case 1:{
                if(grantResults.length >0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                  if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
                      mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack, Looper.myLooper());
                      mMap.setMyLocationEnabled(true);
                  }
                } else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    private void connectDriver(){
        checkLocationPermission();
        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallBack, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }

    private  void disconnectDriver(){
        if(mFusedLocationClient !=null){
            mFusedLocationClient.removeLocationUpdates(mLocationCallBack);
        }
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("DriversAvailable");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }


    @Override
    protected void onStop() {
        super.onStop();


    }

    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};

    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again", Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRoutingStart() {

    }

    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {

        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }

        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            Toast.makeText(getApplicationContext(),"Route "+ (i+1) +": distance - "+ route.get(i).getDistanceValue()+": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRoutingCancelled() {

    }

    private void erasePolylines(){
        for (Polyline line: polylines){
            line.remove();
        }
        polylines.clear();
    }
}
package com.example.uber;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.AutocompleteFragment;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {


    private GoogleMap mMap;

    Location mLastLocation;
    LocationRequest mLocationRequest;

    private FusedLocationProviderClient mFusedLocationClient;

    private Button mLogout, mRequest, mSettings, mHistory;

    private LatLng pickupLocation;

    private Boolean requestBol = false;

    private SupportMapFragment mapFragment;

    private Marker pickupMarker;

    private String destination, requestService;

    private LatLng destinationLatLng;

    private LinearLayout mDriverInfo;

    private ImageView mDriverProfileImage;

    private TextView mDriverName, mDriverPhone, mDriverCar;

    private RadioGroup mRadioGroup;

    private RatingBar mRatingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        destinationLatLng = new LatLng(0.0, 0.0);
        mDriverInfo = (LinearLayout) findViewById(R.id.driverInfo);

        mDriverProfileImage = (ImageView) findViewById(R.id.driverProfileImage);

        mDriverName = (TextView) findViewById(R.id.driverName);
        mDriverPhone = (TextView) findViewById(R.id.driverPhone);
        mDriverCar = (TextView) findViewById(R.id.driverCar);

        mLogout = (Button) findViewById(R.id.logout);
        mRequest = (Button) findViewById(R.id.request);
        mSettings = (Button) findViewById(R.id.settings);
        mHistory = (Button) findViewById(R.id.history);



        mRadioGroup = (RadioGroup) findViewById(R.id.radioGroup);
        mRadioGroup.check((R.id.UberX));

        mRatingBar = (RatingBar) findViewById(R.id.ratingBar);

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });

        mHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(CustomerMapActivity.this, CustomerHistoryActivity.class);
                intent.putExtra("customerOrDriver", "Customers");
                startActivity(intent);
                return;
            }
        });

        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(requestBol){
                    endRide();
                }else{
                    int selectedId = mRadioGroup.getCheckedRadioButtonId();

                    final RadioButton radioButton = (RadioButton) findViewById(selectedId);

                    if(radioButton.getText() == null){
                        return;
                    }

                    requestService = radioButton.getText().toString();
                    requestBol = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("CustomerRequest");

                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    pickupLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLocation).title("Pickup Here"));

                    mRequest.setText("getting your Driver.....");
                    getClosestDriver();

                }

            }
        });
        /**
         * Initialize Places. For simplicity, the API key is hard-coded. In a production
         * environment we recommend using a secure mechanism to manage API keys.
         */
        String apiKey ="AIzaSyCSPuyrfZRp5C6TICvj6FkySjoVtuLiOfE";

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), apiKey);
        }

// Create a new Places client instance.
        PlacesClient placesClient = Places.createClient(this);

        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));

        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                destination = place.getName().toString();
                destinationLatLng = place.getLatLng();


            }

            @Override
            public void onError(Status status) {
                // TODO: Handle the error.

            }
        });
    }
    private int radius = 1;
    private Boolean driverFound = false;
    private String driverFoundID;

    GeoQuery geoQuery;

    private void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("DriversAvailable");

        GeoFire geoFire = new GeoFire(driverLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude, pickupLocation.longitude), radius);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(!driverFound && requestBol) {
                    DatabaseReference mCustomerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(key);
                    mCustomerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if(snapshot.exists() && snapshot.getChildrenCount()>0){
                                Map<String, Object > driverMap = (Map<String, Object>) snapshot.getValue();
                                if(driverFound){
                                    return;
                                }
                                if(driverMap.get("service") .equals(requestService)){
                                    driverFound = true;
                                    driverFoundID = snapshot.getKey();

                                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("CustomerRequest");
                                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                                    HashMap map = new HashMap();
                                    map.put("CustomerRideId", customerId);
                                    map.put("Destination", destination);
                                    map.put("DestinationLat", destinationLatLng.latitude);
                                    map.put("DestinationLng", destinationLatLng.longitude);

                                    driverRef.updateChildren(map);
                                    getDriverLocation();
                                    getAssignedDriverInfo();
                                    getHasRideEnded();
                                    mRequest.setText("Looking For Driver Location...");
                                }
                            }
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {

                        }
                    });
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!driverFound)
                {
                   radius++;
                   getClosestDriver();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });

    }
    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private  ValueEventListener driverLocationRefListener;
    private void getDriverLocation(){
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("DriversWorking").child(driverFoundID).child("l");
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && requestBol){
                    List<Object> map = (List<Object>) snapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    mRequest.setText("DriverFound");
                    if(map.get(0) !=null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) !=null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat,locationLng);
                    if(mDriverMarker !=null) {
                        mDriverMarker.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(pickupLocation.latitude);
                    loc1.setLongitude(pickupLocation.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);

                    if(distance <100){
                        mRequest.setText("Drivers Here");
                    }else{
                        mRequest.setText("Driver Found" + String.valueOf(distance));

                    }

                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("your Driver.."));

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getAssignedDriverInfo(){

        DatabaseReference mCustomerDataBase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        mCustomerDataBase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && snapshot.getChildrenCount()>0){
                    mDriverInfo.setVisibility(View.VISIBLE);
                    Map<String, Object > map = (Map<String, Object>) snapshot.getValue();
                    if (map.get("name") != null){
                        mDriverName.setText( map.get("name").toString());
                    }
                    if (map.get("phone") != null){
                        mDriverPhone.setText( map.get("phone").toString());
                    }
                    if (map.get("car") != null){
                        mDriverCar.setText( map.get("car").toString());
                    }
                    if (map.get("profileImageUrl") != null){
                        Glide.with(getApplicationContext()).load(map.get("profileImageUrl").toString()).into(mDriverProfileImage);

                    }
                    int ratingSum = 0;
                    int ratingsToltal = 0;
                    float ratingAvg = 0;
                    for(DataSnapshot child: snapshot.child("rating").getChildren()){
                            ratingSum = ratingSum = Integer.valueOf(child.getValue().toString());
                            ratingsToltal++;
                    }
                    if(ratingsToltal!= 0){
                        ratingAvg = ratingSum/ratingsToltal;
                        mRatingBar.setRating(ratingAvg);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private DatabaseReference driveHasEndedRef;
    private ValueEventListener driveHasEndedRefListener;
    private void getHasRideEnded(){
        driveHasEndedRef  = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("CustomerRequest").child("CustomerRideId");
        driveHasEndedRefListener = driveHasEndedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){


                }else{
                    endRide();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void endRide(){
        requestBol = false;
        geoQuery.removeAllListeners();
        driverLocationRef.removeEventListener(driverLocationRefListener);
        driveHasEndedRef.removeEventListener(driveHasEndedRefListener);


        if(driverFoundID !=null){
            DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("CustomerRequest");
            driverRef.removeValue();
            driverFoundID = null;

        }

        driverFound = false;
        radius = 1;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("CustomerRequest");
        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);

        if(pickupMarker !=null){
            pickupMarker.remove();

        }
        if(mDriverMarker !=null) {
            mDriverMarker.remove();
        }
        mDriverInfo.setVisibility(View.GONE );
        mDriverProfileImage.setImageResource(R.mipmap.ic_person);
        mRequest.setText("Call Uber");
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){

            }else{
                checkLocationPermission();
            }
        }

        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
        mMap.setMyLocationEnabled(true);
    }


    LocationCallback mLocationCallback = new LocationCallback(){
        @Override
        public void onLocationResult(LocationResult locationResult) {
            for(Location location : locationResult.getLocations()){
                if(getApplicationContext()!=null){
                    mLastLocation = location;

                    LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());

                    mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

                    if(!getDriversAroundStarted){
                        getDriversAround();

                    }
                }
            }
        }
    };

    private void checkLocationPermission() {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)) {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("give permission")
                        .setMessage("give permission message")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                            }
                        })
                        .create()
                        .show();
            }
            else{
                ActivityCompat.requestPermissions(CustomerMapActivity.this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
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
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        mMap.setMyLocationEnabled(true);
                    }
                } else{
                    Toast.makeText(getApplicationContext(), "Please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    boolean getDriversAroundStarted = false;
    List<Marker> markerList = new ArrayList<Marker>();
    private void getDriversAround(){
        getDriversAroundStarted = true;
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("DriversAvailable");
        GeoFire geoFire = new GeoFire(driverLocation);
        GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(),mLastLocation.getLongitude()) ,1000);
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                for(Marker markerIt : markerList){
                    if(markerIt.getTag().equals(key))
                        return;
                }

                LatLng driverLocation = new LatLng(location.latitude,location.longitude);

                Marker mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLocation).title(key));
                mDriverMarker.setTag(key);
                markerList.add(mDriverMarker);
            }

            @Override
            public void onKeyExited(String key) {
                for(Marker markerIt : markerList){
                    if(markerIt.getTag().equals(key)){
                        markerIt.remove();
                        markerList.remove(markerIt);
                        return;
                    }
                }
            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {
                for(Marker markerIt : markerList){
                    if(markerIt.getTag().equals(key)){
                        markerIt.setPosition(new LatLng(location.latitude,location.longitude));
                    }
                }
            }

            @Override
            public void onGeoQueryReady() {

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();

    }
}
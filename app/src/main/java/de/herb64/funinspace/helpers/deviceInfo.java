package de.herb64.funinspace.helpers;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Debug;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;

import java.util.Locale;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Created by herbert on 9/13/17.
 */
// https://stackoverflow.com/questions/36947709/how-do-i-fix-or-correct-the-default-file-template-warning-in-intellij-idea

public class deviceInfo {

    private Context ctx;
    private Locale loc;

    private int sdkVersion;
    private String brand;
    private String model;
    private String release;
    private String device;
    private String product;

    private float totalMem;
    private float availMem;
    private float threshold;
    private boolean lowMemory;
    private int memClass;
    private int largeMemClass;
    private String lowRamDev;

    // max alloc test
    private int maxAlloc = 0;

    private float nativeHeapSize;
    private float nativeHeapFreeSize;
    private float nativeHeapAllocatedSize ;
    private float dalvikPss;
    private float dalvikPD;
    private float dalvikSD;
    private float nativePss;
    private float nativePD;
    private float nativeSD;
    private float otherPss;
    private float otherPD;
    private float otherSD;

    private float maxMem;
    private float totMem;
    private float freeMem;
    private float usedMem ;
    private float totalFreeMem;

    private String ssid;
    private String ipAddress;
    private boolean wifiActive;

    // doing GL_MAX_TEXTURESIZE in MainActivity now only via shared preferences
    private int maxTexSize = 0;       // set from outside, not queried within this class

    private static final int KIB = 1024;
    private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;

    // Constructor loads all values at construction time
    public deviceInfo(Context context) {
        this.ctx = context;
        // avoid complaints on String.format()...
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = ctx.getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = ctx.getResources().getConfiguration().locale;
        }
        getBuildAndFeatureInfo();           // Device: build and features
        getMemoryInfo(true, true, true);    // Memory: actmgr/debug/runtime
        getNetInfo();                       // Network
    }

    // ---------- memory checker -------------
    // memory checker code to get maximum possible contiguous allocation
    public int getMaxAllocatable() {
        maxAlloc = 0;
        ActivityManager actMgr = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
        int memClass = actMgr.getMemoryClass();
        allocTest(memClass, memClass / 2);
        return maxAlloc;
    }

    // RECURSIVE test function for memory allocations
    private void allocTest(int size, int step) {
        int result = makeBigData(size * 1024 * 1024);
        if (size > maxAlloc && result == 1) {
            maxAlloc = size;
        }
        if (step > 1) {                         // stopping here, not going to the last byte
            allocTest(size + step * result, step / 2);
        }
    }

    // Allocate a byte array of given size in bytes
    private int makeBigData(int size) {
        try {
            //noinspection unused
            byte[] bigData = new byte[size];    // Alloc with new already sufficient for OOM
            //for (int i = 0; i < size; i++) {  // Fill of data needed to actually show
            //    bigData[i] = 0;               // up in memory monitor - (this could be
            //}                                 // theoretically skipped...)
            return 1;                           // return +1 on successful allocation
        } catch (OutOfMemoryError e) {          // e.toString() - to be logged in logcat
            return -1;                          // return -1 on OOM condition
        }
    }
    // ------------ end memory checker ------

    private void getBuildAndFeatureInfo() {
        // Availabe features on the phone TODO finalize
        PackageManager pM = ctx.getPackageManager();
        FeatureInfo[] fInfo = pM.getSystemAvailableFeatures();
        /*for (FeatureInfo aFInfo : fInfo) {
            Log.i("HFCM", aFInfo.toString());
        }*/
        // Build infos
        sdkVersion = Build.VERSION.SDK_INT;
        brand = Build.BRAND;
        model = Build.MODEL;
        release = Build.VERSION.RELEASE;
        device = Build.DEVICE;
        product = Build.PRODUCT;
    }

    private void getMemoryInfo(boolean bActmgr, boolean bDebug, boolean bRuntime) {
        // Memory related infos obtained via ActivityManager
        if (bActmgr) {
            ActivityManager activityManager = (ActivityManager) ctx.getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            totalMem = (float) memoryInfo.totalMem / MIB;
            availMem = (float) memoryInfo.availMem / MIB;
            threshold = (float) memoryInfo.threshold / MIB;
            lowMemory = memoryInfo.lowMemory;
            memClass = activityManager.getMemoryClass();    // heap size, see CruQY55HOk, 05:00
            largeMemClass = activityManager.getLargeMemoryClass();
            lowRamDev = "n/a";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if (activityManager.isLowRamDevice()) {
                    lowRamDev = "true";
                } else {
                    lowRamDev = "false";
                }
            }
            getMaxAllocatable();    // this sets maxAlloc
        }

        // Memory related infos obtaine via Debug
        if (bDebug) {
            nativeHeapSize = (float) Debug.getNativeHeapSize() / MIB;
            nativeHeapFreeSize = (float) Debug.getNativeHeapFreeSize() / MIB;
            nativeHeapAllocatedSize = (float) Debug.getNativeHeapAllocatedSize() / MIB;
            Debug.MemoryInfo dmemInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(dmemInfo);
            dalvikPss = (float) dmemInfo.dalvikPss / KIB;
            dalvikPD = (float) dmemInfo.dalvikPrivateDirty / KIB;
            dalvikSD = (float) dmemInfo.dalvikSharedDirty / KIB;
            nativePss = (float) dmemInfo.nativePss / KIB;
            nativePD = (float) dmemInfo.nativePrivateDirty / KIB;
            nativeSD = (float) dmemInfo.nativeSharedDirty / KIB;
            otherPss = (float) dmemInfo.otherPss / KIB;
            otherPD = (float) dmemInfo.otherPrivateDirty / KIB;
            otherSD = (float) dmemInfo.otherSharedDirty / KIB;
        }

        // Memory related nfos obtained via Runtime
        if (bRuntime) {
            maxMem = (float) Runtime.getRuntime().maxMemory() / MIB;      // max useable by app - seems to be same as memoryclass value in general
            totMem = (float) Runtime.getRuntime().totalMemory() / MIB;    // current heapsize
            freeMem = (float) Runtime.getRuntime().freeMemory() / MIB;    // available on heap
            // https://stackoverflow.com/questions/3571203/what-are-runtime-getruntime-totalmemory-and-freememory
            usedMem = totMem - freeMem;
            totalFreeMem = maxMem - usedMem;  // but is this contiguous, as stated in link below ?
        }
    }

    public String getDeviceInfo() {
        return String.format(loc, "=== Device ===\n" +
                "Brand/Model: %s / %s\n" +
                "Product: %s\n" +
                "Device: %s\n" +
                "Release: %s\n" +
                "Device SDK Version: %d\n",
                brand,
                model,
                product,
                device,
                release,
                sdkVersion);
    }

    public String getActMgrMemoryInfo(boolean bRefresh) {
        if (bRefresh) {
            getMemoryInfo(true, false, false);
        }
        return String.format(loc, "=== ActivityMgr ===\n" +
                        "Total/AvailMem: %.2f/%.2f MiB\n" +
                        "Threshold: %.2f MiB \n" +
                        "LowMemory: %b\n" +
                        "MemoryClass: %d MiB\n" +
                        "LargeMemoryClass: %d MiB\n" +
                        "IsLowRamDevice: %s\n" +
                        "MaxAllocatable:  %d\n",
                totalMem,
                availMem,
                threshold,
                lowMemory,
                memClass,
                largeMemClass,
                lowRamDev,
                maxAlloc);
    }

    public String getDebugMemoryInfo(boolean bRefresh) {
        if (bRefresh) {
            getMemoryInfo(false, true, false);
        }
        return String.format(loc, "=== Debug ===\n" +
                        "NativeHeapSize: %.2f MiB\n" +
                        "NativeHeapFreeSize: %.2f MiB\n" +
                        "NativeHeapAllocatedSize: %.2f MiB\n" +
                        "PSS/PrivateDirty/SharedDirty\n" +
                        "Dalvik: %.2f/%.2f/%.2f MiB\n" +
                        "Native: %.2f/%.2f/%.2f MiB\n" +
                        "Other: %.2f/%.2f/%.2f MiB\n",
                nativeHeapSize,
                nativeHeapFreeSize,
                nativeHeapAllocatedSize,
                dalvikPss,
                dalvikPD,
                dalvikSD,
                nativePss,
                nativePD,
                nativeSD,
                otherPss,
                otherPD,
                otherSD);
    }

    public String getRuntimeMemoryInfo(boolean bRefresh) {
        if (bRefresh) {
            getMemoryInfo(false, false, true);
        }
        return String.format(loc, "=== Runtim e===\n" +
                        "Total/Max/Free\n" +
                        "%.2f/%.2f/%.2f MiB\n" +
                        "Used/TotalFree\n" +
                        "%.2f/%.2f\n",
                totMem,
                maxMem,
                freeMem,
                usedMem,
                totalFreeMem);
    }


    private void getNetInfo() {
        ssid = " n/a ";
        ipAddress = " n/a ";
        wifiActive = false;
        ConnectivityManager connManager = (ConnectivityManager) ctx.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            // Important!!! wifi service needs to be looked up on application context to avoid memory leaks
            final WifiManager wM = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wM.getConnectionInfo();
            if (connectionInfo != null &&
                    !TextUtils.isEmpty(connectionInfo.getSSID()) &&
                    !TextUtils.isEmpty(Formatter.formatIpAddress(connectionInfo.getIpAddress()))) {
                ssid = connectionInfo.getSSID();
                ipAddress = Formatter.formatIpAddress(connectionInfo.getIpAddress());
                wifiActive = true;
            }
        }
    }

    public String getNetworkInfo(boolean bRefresh) {
        if (bRefresh) {
            getNetInfo();
        }
        return String.format(loc, "=== Network ===\n"+
                "SSID: %s\nIP Address: %s",
                ssid,
                ipAddress);
    }

    public boolean isWifiActive() {
        getNetInfo();
        return wifiActive;
    }

    public void setGlMaxTextureSize(int maxTexSize) {
        this.maxTexSize = maxTexSize;
    }

    public String getGLInfo() {
        return String.format(loc, "=== OpenGL ===\n" +
                        "Max Texture Size: %d\n",
                maxTexSize);
    }
}

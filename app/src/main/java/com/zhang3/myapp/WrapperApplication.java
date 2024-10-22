package com.zhang3.myapp;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.util.ArrayMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import dalvik.system.DexClassLoader;

public class WrapperApplication extends Application {
    private static final String ALGORITHM = "DES";
    private static final String TRANSFORMATION = "DES/ECB/PKCS5Padding";

    public void onCreate() {
        super.onCreate();

        // 替换Application
        try {
            Class<?> ActivityThread = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = ActivityThread.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object currentActivityThreadInstance = currentActivityThread.invoke(null);

            Field mBoundApplicationField = ActivityThread.getDeclaredField("mBoundApplication");
            mBoundApplicationField.setAccessible(true);
            Object mBoundApplication = mBoundApplicationField.get(currentActivityThreadInstance);

            Class<?> AppBindData = Class.forName("android.app.ActivityThread$AppBindData");
            Field infoField = AppBindData.getDeclaredField("info");
            infoField.setAccessible(true);
            Object loadedApkInfo = infoField.get(mBoundApplication);

            Class<?> LoadedApk = Class.forName("android.app.LoadedApk");
            Field mApplicationField = LoadedApk.getDeclaredField("mApplication");
            mApplicationField.setAccessible(true);
            mApplicationField.set(loadedApkInfo, null);

            String className = "com.zhang3.myapp.MyApplication";
            Field mApplicationInfoField = LoadedApk.getDeclaredField("mApplicationInfo");
            mApplicationInfoField.setAccessible(true);
            ApplicationInfo applicationInfo = (ApplicationInfo) mApplicationInfoField.get(loadedApkInfo);
            applicationInfo.className = className;

            Field appInfoField = AppBindData.getDeclaredField("appInfo");
            appInfoField.setAccessible(true);
            ApplicationInfo appInfo = (ApplicationInfo) appInfoField.get(mBoundApplication);
            appInfo.className = className;

            Field mInitialApplicationField = ActivityThread.getDeclaredField("mInitialApplication");
            mInitialApplicationField.setAccessible(true);
            Application initialApplication = (Application) mInitialApplicationField.get(currentActivityThreadInstance);

            Field mAllApplicationsField = ActivityThread.getDeclaredField("mAllApplications");
            mAllApplicationsField.setAccessible(true);
            ArrayList<Application> mAllApplications = (ArrayList<Application>) mAllApplicationsField.get(currentActivityThreadInstance);
            mAllApplications.remove(initialApplication);

            Method makeApplication = LoadedApk.getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
            makeApplication.setAccessible(true);
            Application newApplication = (Application) makeApplication.invoke(loadedApkInfo, false, null);
            newApplication.onCreate();

            mInitialApplicationField.set(currentActivityThreadInstance, newApplication);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        String targetDirectory = getDir("temp", MODE_PRIVATE).getAbsolutePath();
        String targetPath = targetDirectory + "/decrypted.dex";

        File outputFile = new File(targetPath);
        String inputFile = "encrypted.png";

        AssetManager assetManager = getAssets();
        String key = "abcdefgh";
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), ALGORITHM);

        try {
            InputStream inputStream = assetManager.open(inputFile);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            // 简单的拷贝 可以在此处进行更复杂的加解密操作
            byte[] inputBytes = new byte[64];
            int bytesRead;
            while ((bytesRead = inputStream.read(inputBytes)) != -1) {
                byte[] outputBytes = cipher.update(inputBytes, 0, bytesRead);
                if (outputBytes != null) {
                    outputStream.write(outputBytes);
                }
            }
            byte[] outputBytes = cipher.doFinal(); // 完成解密
            if (outputBytes != null) {
                outputStream.write(outputBytes);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchPaddingException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            throw new RuntimeException(e);
        }

        DexClassLoader dexClassLoader = new DexClassLoader(
                targetPath,
                targetDirectory,
                getApplicationInfo().nativeLibraryDir,
                getClassLoader()
        );

        // 替换classLoader
        try {
            Class<?> ActivityThread = Class.forName("android.app.ActivityThread");
            Method currentActivityThread = ActivityThread.getDeclaredMethod("currentActivityThread");
            currentActivityThread.setAccessible(true);
            Object currentActivityThreadInstance = currentActivityThread.invoke(null);

            Field mPackagesField = ActivityThread.getDeclaredField("mPackages");
            mPackagesField.setAccessible(true);
            ArrayMap mPackages = (ArrayMap) mPackagesField.get(currentActivityThreadInstance);
            String packageName = getPackageName();
            WeakReference wr = (WeakReference) mPackages.get(packageName);

            Class<?> LoadedApk = Class.forName("android.app.LoadedApk");
            Field mClassLoaderField = LoadedApk.getDeclaredField("mClassLoader");
            mClassLoaderField.setAccessible(true);
            mClassLoaderField.set(wr.get(), dexClassLoader);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}

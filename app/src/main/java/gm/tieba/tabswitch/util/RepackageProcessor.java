package gm.tieba.tabswitch.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.apksigner.ApkSignerTool;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

import bin.zip.ZipEntry;
import bin.zip.ZipOutputStream;
import gm.tieba.tabswitch.R;

public class RepackageProcessor {
    public static AlertDialog xpatchSuccessDialog;
    public static AlertDialog xpatchFailureDialog;
    public static AlertDialog xpatchStartDialog;
    public static int recyclerViewId;
    public static File inApk;
    public static File outApk;
    public static byte[] manifestData;
    public static String apkSignInfo;
    public static long writtenSize;
    public static List<Map<String, Object>> moduleList;
    public static ArrayList<String> insertModulesPath;
    public static Thread getSignThread;

    public static void initDialog(Activity activity) {
        xpatchSuccessDialog = new AlertDialog.Builder(activity)
                .setIcon(R.mipmap.ic_launcher).setTitle("处理完成").setCancelable(false)
                .setNegativeButton("取消", (dialogInterface, i) -> {
                }).setPositiveButton("确定", (dialogInterface, i) -> {
                }).create();
        xpatchFailureDialog = new AlertDialog.Builder(activity)
                .setIcon(R.mipmap.ic_launcher).setTitle("处理失败").setCancelable(true)
                .setPositiveButton("确定", (dialogInterface, i) -> {
                }).create();

        SwitchCompat signSwitch = new SwitchCompat(activity);
        SwitchCompat appSwitch = new SwitchCompat(activity);
        SwitchCompat moduleListSwitch = new SwitchCompat(activity);
        TextView tipTextView = new TextView(activity);
        RecyclerView recyclerView = new RecyclerView(activity);
        if (DisplayHelper.isLightMode(activity)) {
            signSwitch.setTextColor(activity.getResources().getColor(R.color.colorPrimaryDark, null));
            appSwitch.setTextColor(activity.getResources().getColor(R.color.colorPrimaryDark, null));
            moduleListSwitch.setTextColor(activity.getResources().getColor(R.color.colorPrimaryDark, null));
        } else {
            signSwitch.setTextColor(activity.getResources().getColor(R.color.colorPrimary, null));
            appSwitch.setTextColor(activity.getResources().getColor(R.color.colorPrimary, null));
            moduleListSwitch.setTextColor(activity.getResources().getColor(R.color.colorPrimary, null));
        }
        signSwitch.setTextSize(16);
        signSwitch.setText("跳过读取签名");
        appSwitch.setTextSize(16);
        appSwitch.setText("替换原始安装包");
        moduleListSwitch.setTextSize(16);
        moduleListSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (b) {
                tipTextView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tipTextView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });
        moduleListSwitch.setText("跳过内置加载模块列表");
        tipTextView.setTextSize(14);
        tipTextView.setPadding(0, 12, 0, 0);
        tipTextView.setTextColor(activity.getResources().getColor(R.color.colorGray, null));
        tipTextView.setText("加载模块列表");
        recyclerViewId = View.generateViewId();
        recyclerView.setId(recyclerViewId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity) {
            @Override
            public RecyclerView.LayoutParams generateDefaultLayoutParams() {
                return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        };
        recyclerView.setLayoutManager(layoutManager);
        LinearLayout linearLayout = new LinearLayout(activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(50, 0, 50, 0);
        linearLayout.addView(signSwitch);
        linearLayout.addView(appSwitch);
        linearLayout.addView(moduleListSwitch);
        linearLayout.addView(tipTextView);
        linearLayout.addView(recyclerView);
        xpatchStartDialog = new AlertDialog.Builder(activity)
                .setIcon(R.mipmap.ic_launcher).setTitle("打包设置").setView(linearLayout).setCancelable(false)
                .setNeutralButton("内置模块", (dialogInterface, i) -> {
                }).setNegativeButton("取消", (dialogInterface, i) -> {
                    getSignThread.interrupt();
                    manifestData = null;
                }).setPositiveButton("确定", (dialogInterface, i) -> {
                    Map<String, Boolean> xpatchPreference = new HashMap<>();
                    xpatchPreference.put("signSwitch", signSwitch.isChecked());
                    xpatchPreference.put("appSwitch", appSwitch.isChecked());
                    xpatchPreference.put("moduleListSwitch", moduleListSwitch.isChecked());
                    executeXpatch(activity, xpatchPreference);
                }).create();
    }

    public static class GetSignTask implements Runnable {
        @Override
        public void run() {
            apkSignInfo = XpatchAssetHelper.getApkSignInfo(inApk.getPath());
        }
    }

    public static void executeXpatch(Activity activity, Map<String, Boolean> xpatchPreference) {
        RelativeLayout progressContainer = activity.findViewById(R.id.progress_container);
        progressContainer.setVisibility(View.VISIBLE);
        TextView progressBackground = activity.findViewById(R.id.progress_background);
        progressBackground.setVisibility(View.GONE);
        TextView apkName = activity.findViewById(R.id.apk_name);
        outApk = new File(activity.getExternalCacheDir().getAbsolutePath(), inApk.getName().replace(".apk", "_xpatch.apk"));
        new Thread(() -> {
            try {
                activity.runOnUiThread(() -> apkName.setText(String.format("%s：读取签名", inApk.getName())));
                if (xpatchPreference.get("signSwitch")) getSignThread.interrupt();
                else {
                    getSignThread.join();
                    if (apkSignInfo == null) throw new NullPointerException("无v1签名");
                }
                activity.runOnUiThread(() -> apkName.setText(String.format("%s：读取ZipEntry", inApk.getName())));
                try (ZipOutputStream zos = new ZipOutputStream(outApk)) {
                    int dexCount = 1;
                    boolean haveWrittenV8a = false;
                    boolean haveWrittenV7a = false;
                    boolean haveWrittenArmeabi = false;
                    long extraSize = 0;
                    long inApkSize = inApk.length();
                    if (xpatchPreference.get("appSwitch")) extraSize += inApkSize;
                    zos.setLevel(Deflater.HUFFMAN_ONLY);

                    bin.zip.ZipFile zipFile = new bin.zip.ZipFile(inApk);
                    Enumeration<ZipEntry> enumeration = zipFile.getEntries();
                    while (enumeration.hasMoreElements()) {
                        ZipEntry ze = enumeration.nextElement();
                        if (ze.getName().equals("AndroidManifest.xml")) continue;
                        else if (ze.getName().matches("classes[0-9]*?\\.dex")) dexCount++;
                        else if (ze.getName().startsWith("lib/arm64-v8a/") && !haveWrittenV8a) {
                            InputStream v8a = activity.getAssets().open("arm64-v8a/libsandhook.so");
                            extraSize += v8a.available();
                            zos.putNextEntry("lib/arm64-v8a/libsandhook.so");
                            zos.writeFully(v8a);
                            zos.closeEntry();
                            v8a = activity.getAssets().open("arm64-v8a/libsandhook-native.so");
                            extraSize += v8a.available();
                            zos.putNextEntry("lib/arm64-v8a/libsandhook-native.so");
                            zos.writeFully(v8a);
                            zos.closeEntry();
                            for (int j = 0; j < insertModulesPath.size(); j++) {
                                FileInputStream insertModule = new FileInputStream(insertModulesPath.get(j));
                                extraSize += insertModule.available();
                                zos.putNextEntry("lib/arm64-v8a/libxpatch_xp_module_" + j + ".so");
                                zos.writeFully(insertModule);
                                zos.closeEntry();
                            }
                            haveWrittenV8a = true;
                        } else if (ze.getName().startsWith("lib/armeabi-v7a/") && !haveWrittenV7a) {
                            InputStream v7a = activity.getAssets().open("armeabi-v7a/libsandhook.so");
                            extraSize += v7a.available();
                            zos.putNextEntry("lib/armeabi-v7a/libsandhook.so");
                            zos.writeFully(v7a);
                            zos.closeEntry();
                            v7a = activity.getAssets().open("armeabi-v7a/libsandhook-native.so");
                            extraSize += v7a.available();
                            zos.putNextEntry("lib/armeabi-v7a/libsandhook-native.so");
                            zos.writeFully(v7a);
                            zos.closeEntry();
                            for (int j = 0; j < insertModulesPath.size(); j++) {
                                FileInputStream insertModule = new FileInputStream(insertModulesPath.get(j));
                                extraSize += insertModule.available();
                                zos.putNextEntry("lib/armeabi-v7a/libxpatch_xp_module_" + j + ".so");
                                zos.writeFully(insertModule);
                                zos.closeEntry();
                            }
                            haveWrittenV7a = true;
                        } else if (ze.getName().startsWith("lib/armeabi/") && !haveWrittenArmeabi) {
                            InputStream v7a = activity.getAssets().open("armeabi-v7a/libsandhook.so");
                            extraSize += v7a.available();
                            zos.putNextEntry("lib/armeabi/libsandhook.so");
                            zos.writeFully(v7a);
                            zos.closeEntry();
                            v7a = activity.getAssets().open("armeabi-v7a/libsandhook-native.so");
                            extraSize += v7a.available();
                            zos.putNextEntry("lib/armeabi/libsandhook-native.so");
                            zos.writeFully(v7a);
                            zos.closeEntry();
                            for (int j = 0; j < insertModulesPath.size(); j++) {
                                FileInputStream insertModule = new FileInputStream(insertModulesPath.get(j));
                                extraSize += insertModule.available();
                                zos.putNextEntry("lib/armeabi/libxpatch_xp_module_" + j + ".so");
                                zos.writeFully(insertModule);
                                zos.closeEntry();
                            }
                            haveWrittenArmeabi = true;
                        }
                        zos.copyZipEntry(ze, zipFile);
                        long finalExtraSize = extraSize;
                        activity.runOnUiThread(() -> {
                            apkName.setText(String.format("%s：写出安装包", inApk.getName()));
                            float progress = (float) writtenSize / (inApkSize + finalExtraSize);
                            ViewGroup.LayoutParams lp = progressBackground.getLayoutParams();
                            lp.height = apkName.getHeight();
                            lp.width = (int) (progressContainer.getWidth() * progress);
                            progressBackground.setLayoutParams(lp);
                            progressBackground.setVisibility(View.VISIBLE);
                        });
                    }
                    zos.putNextEntry("AndroidManifest.xml");
                    zos.write(manifestData);
                    zos.closeEntry();
                    if (ManifestParser.applicationName != null) {
                        zos.putNextEntry("assets/xpatch_asset/original_application_name.ini");
                        zos.write(ManifestParser.applicationName.getBytes());
                        zos.closeEntry();
                    }
                    if (!xpatchPreference.get("signSwitch")) {
                        zos.putNextEntry("assets/xpatch_asset/original_signature_info.ini");
                        zos.write(apkSignInfo.getBytes());
                        zos.closeEntry();
                    }
                    if (xpatchPreference.get("appSwitch")) {
                        zos.putNextEntry("assets/xpatch_asset/original_app.apk");
                        zos.writeFully(new FileInputStream(inApk));
                        zos.closeEntry();
                    }
                    if (!xpatchPreference.get("moduleListSwitch")) {
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < moduleList.size(); j++) {
                            sb.append("\n\n");
                            Map<String, Object> map = moduleList.get(j);
                            if (!(boolean) map.get("isChecked")) sb.append("#");
                            sb.append(map.get("packageName")).append("#").append(map.get("apkName"));
                        }
                        zos.putNextEntry("assets/xpatch_asset/original_module_list.ini");
                        zos.write(sb.toString().getBytes());
                        zos.closeEntry();
                    }
                    zos.putNextEntry("classes" + dexCount + ".dex");
                    zos.writeFully(activity.getAssets().open("classes.dex"));
                    zos.closeEntry();
                    if (!haveWrittenV8a && !haveWrittenV7a && !haveWrittenArmeabi) {
                        zos.putNextEntry("lib/armeabi/libsandhook.so");
                        zos.writeFully(activity.getAssets().open("armeabi-v7a/libsandhook.so"));
                        zos.closeEntry();
                        zos.putNextEntry("lib/armeabi/libsandhook-native.so");
                        zos.writeFully(activity.getAssets().open("armeabi-v7a/libsandhook-native.so"));
                        zos.closeEntry();
                        for (int j = 0; j < insertModulesPath.size(); j++) {
                            zos.putNextEntry("lib/armeabi/libxpatch_xp_module_" + j + ".so");
                            zos.writeFully(new FileInputStream(insertModulesPath.get(j)));
                            zos.closeEntry();
                        }
                    }
                }
                activity.runOnUiThread(() -> {
                    apkName.setText(String.format("%s：签名", inApk.getName()));
                    ViewGroup.LayoutParams lp = progressBackground.getLayoutParams();
                    lp.width = progressContainer.getWidth();
                    progressBackground.setLayoutParams(lp);
                });
                String keystorePath = activity.getExternalCacheDir().getAbsolutePath() + File.separator + "key";
                IO.copyFile(activity.getAssets().open("android.keystore"), keystorePath);
                String[] signParams = {"sign",
                        "--ks", keystorePath,
                        "--ks-key-alias", "key0",
                        "--ks-pass", "pass:" + "123456",
                        "--key-pass", "pass:" + "123456",
                        "--v1-signing-enabled", "" + false,
                        "--v2-signing-enabled", "" + true,
                        "--out", outApk.getPath(),
                        outApk.getPath()};
                try {
                    ApkSignerTool.main(signParams);
                } catch (NoSuchMethodError e) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                        throw new NoSuchMethodError("签名失败");
                } finally {
                    new File(keystorePath).delete();
                }
                String packageName = ManifestParser.packageName;
                activity.runOnUiThread(() -> {
                    activity.findViewById(R.id.progress_container).setVisibility(View.GONE);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    Uri uri = FileProvider.getUriForFile(activity, "gm.FileProvider", outApk);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    try {
                        if (activity.getPackageManager().getPackageInfo(ManifestParser.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.getApkContentsSigners()[0].hashCode() == -524758043)
                            throw new PackageManager.NameNotFoundException();
                        xpatchSuccessDialog.setMessage(outApk.getName() + "\n安卓系统不允许覆盖安装签名不同的同包名应用，您已经安装了签名不同的安装包，需要先卸载它才能安装 Xpatch 安装包。");
                        xpatchSuccessDialog.show();
                        xpatchSuccessDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                            Intent uninstallIntent = new Intent();
                            uninstallIntent.setAction(Intent.ACTION_DELETE);
                            uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                            uninstallIntent.setData(Uri.parse("package:" + packageName));
                            activity.startActivityForResult(uninstallIntent, 0);
                        });
                    } catch (PackageManager.NameNotFoundException e) {
                        activity.startActivity(intent);
                        xpatchSuccessDialog.dismiss();
                    }
                });
            } catch (Throwable throwable) {
                activity.runOnUiThread(() -> {
                    activity.findViewById(R.id.progress_container).setVisibility(View.GONE);
                    xpatchFailureDialog.setMessage(inApk.getName() + "\n" + Log.getStackTraceString(throwable));
                    xpatchFailureDialog.show();
                });
                throwable.printStackTrace();
            } finally {
                writtenSize = 0;
                manifestData = null;
                apkSignInfo = null;
            }
        }).start();
    }
}
package gm.tieba.tabswitch.hookImpl;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import org.jf.dexlib.ClassDataItem;
import org.jf.dexlib.ClassDefItem;
import org.jf.dexlib.DexFile;
import org.jf.util.IndentingWriter2;
import org.jf.util.Parser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.Hook;

public class AntiConfusionHelper extends Hook {
    public static List<String> matcherList = new ArrayList<>();

    static {
        addMatcher(matcherList);
    }

    private static void addMatcher(List<String> list) {
        //TSPreference
        list.add("Lcom/baidu/tieba/R$id;->black_address_list:I");
        //TSPreferenceHelper
        list.add("Lcom/baidu/tieba/R$layout;->dialog_bdalert:I");
        //Purify
        list.add("\"c/s/splashSchedule\"");//旧启动广告
        list.add("\"custom_ext_data\"");//sdk启动广告
        list.add("Lcom/baidu/tieba/recapp/lego/model/AdCard;-><init>(Lorg/json/JSONObject;)V");//卡片广告
        list.add("\"pic_amount\"");//图片广告
        list.add("\"key_frs_dialog_ad_last_show_time\"");//吧推广弹窗
        list.add("Lcom/baidu/tieba/R$id;->frs_ad_banner:I");//吧推广横幅
        list.add("Lcom/baidu/tieba/R$string;->mark_like:I");//关注作者追帖更简单
        list.add("Lcom/baidu/tieba/R$layout;->pb_child_title:I");//视频相关推荐
        //PurifyEnter
        list.add("Lcom/baidu/tieba/R$id;->square_background:I");//吧广场
        list.add("Lcom/baidu/tieba/R$id;->create_bar_container:I");//创建自己的吧
        //PurifyMy
        list.add("Lcom/baidu/tieba/R$drawable;->icon_pure_topbar_store44_svg:I");//商店
        list.add("Lcom/baidu/tieba/R$id;->function_item_bottom_divider:I");//分割线
        list.add("\"https://tieba.baidu.com/mo/q/duxiaoman/index?noshare=1\"");//我的ArrayList
        //CreateView
        list.add("Lcom/baidu/tieba/R$id;->navigationBarGoSignall:I");
        //ThreadStore
        list.add("\"c/f/post/threadstore\"");
        //NewSub
        list.add("Lcom/baidu/tieba/R$id;->subpb_head_user_info_root:I");
        //MyAttention
        list.add("Lcom/baidu/tieba/R$layout;->person_list_item:I");
        //StorageRedirect
        list.add("0x4197d783fc000000L");
        //ForbidGesture
        list.add("Lcom/baidu/tieba/R$id;->new_pb_list:I");
    }

    public static List<String> getLostList() {
        List<String> ruleList = new ArrayList<>();
        for (int i = 0; i < ruleMapList.size(); i++) {
            Map<String, String> map = ruleMapList.get(i);
            ruleList.add(map.get("rule"));
        }
        List<String> lostList = new ArrayList<>(matcherList);
        lostList.removeAll(ruleList);
        return lostList;
    }

    public static boolean isVersionChanged(Context context) {
        SharedPreferences tsConfig = context.getSharedPreferences("TS_config", Context.MODE_PRIVATE);
        return !tsConfig.getString("anti-confusion_version", "unknown").equals(getTbVersion(context));
    }

    public static boolean isDexChanged(Context context) {
        try {
            SharedPreferences tsConfig = context.getSharedPreferences("TS_config", Context.MODE_PRIVATE);
            bin.zip.ZipFile zipFile = new bin.zip.ZipFile(new File(context.getPackageResourcePath()));
            byte[] bytes = new byte[32];
            zipFile.getInputStream(zipFile.getEntry("classes.dex")).read(bytes);
            DexFile.calcSignature(bytes);
            return Arrays.hashCode(bytes) != tsConfig.getInt("signature", 0);
        } catch (IOException e) {
            XposedBridge.log(e);
        }
        return false;
    }

    @SuppressLint("ApplySharedPref")
    public static void saveAndRestart(Activity activity, String value, Class<?> springboardActivity) {
        SharedPreferences.Editor editor = activity.getSharedPreferences("TS_config", Context.MODE_PRIVATE).edit();
        editor.putString("anti-confusion_version", value);
        editor.commit();
        if (springboardActivity != null) {
            XposedHelpers.findAndHookMethod(springboardActivity, "onCreate", Bundle.class, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(intent);
                    }
                    System.exit(0);
                }
            });
            Intent intent = new Intent(activity, springboardActivity);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
        } else {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            }
            System.exit(0);
        }
    }

    static void searchAndSave(ClassDefItem classItem, int type, SQLiteDatabase db) throws IOException {
        ClassDataItem.EncodedMethod[] methods = null;
        try {
            if (type == 0) methods = classItem.getClassData().getDirectMethods();
            else if (type == 1) methods = classItem.getClassData().getVirtualMethods();
        } catch (NullPointerException e) {
            return;
        }
        for (ClassDataItem.EncodedMethod method : methods) {
            Parser parser = new Parser(method.codeItem);
            IndentingWriter2 writer = new IndentingWriter2();
            parser.dump(writer);
            for (int i = 0; i < matcherList.size(); i++)
                if (writer.getString().contains(matcherList.get(i))) {
                    String clazz = classItem.getClassType().getTypeDescriptor();
                    clazz = clazz.substring(clazz.indexOf("L") + 1, clazz.indexOf(";")).replace("/", ".");
                    db.execSQL("insert into rules(rule,class,method) values(?,?,?)", new Object[]{matcherList.get(i), clazz, method.method.methodName.getStringValue()});
                    return;
                }
        }
    }

    public static List<Map<String, String>> convertDbToMapList(SQLiteDatabase db) {
        List<Map<String, String>> dbDataList = new ArrayList<>();
        Cursor c = db.query("rules", null, null, null, null, null, null);
        for (int j = 0; j < c.getCount(); j++) {
            c.moveToNext();
            Map<String, String> map = new HashMap<>();
            map.put("rule", c.getString(1));
            map.put("class", c.getString(2));
            map.put("method", c.getString(3));
            dbDataList.add(map);
        }
        c.close();
        db.close();
        return dbDataList;
    }

    public static String getTbVersion(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo applicationInfo = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
            switch ((Integer) applicationInfo.metaData.get("versionType")) {
                case 3:
                    return pm.getPackageInfo(context.getPackageName(), 0).versionName;
                case 2:
                    return String.valueOf(applicationInfo.metaData.get("grayVersion"));
                case 1:
                    return String.valueOf(applicationInfo.metaData.get("subVersion"));
                default:
                    throw new PackageManager.NameNotFoundException("unknown tb version");
            }
        } catch (PackageManager.NameNotFoundException e) {
            XposedBridge.log(e);
            return "unknown";
        }
    }
}
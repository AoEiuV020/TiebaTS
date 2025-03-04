package gm.tieba.tabswitch.hooker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.BuildConfig;
import gm.tieba.tabswitch.hooker.TSPreferenceHelper.SwitchViewHolder;
import gm.tieba.tabswitch.hooker.model.BaseHooker;
import gm.tieba.tabswitch.hooker.model.Hooker;
import gm.tieba.tabswitch.hooker.model.Preferences;
import gm.tieba.tabswitch.hooker.model.Rule;
import gm.tieba.tabswitch.hooker.model.TbDialogBuilder;
import gm.tieba.tabswitch.util.DisplayHelper;
import gm.tieba.tabswitch.util.Reflect;

public class TSPreference extends BaseHooker implements Hooker {
    private static boolean sIsShowTSPreference = false;
    private static int sCount = 0;

    public TSPreference(ClassLoader classLoader, Resources res) {
        super(classLoader, res);
    }

    public void hook() throws Throwable {
        XposedHelpers.findAndHookMethod("com.baidu.tieba.LogoActivity", sClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (activity.getIntent().getBooleanExtra("showTSPreference", false)) {
                    sIsShowTSPreference = true;
                }
            }
        });
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                if (sIsShowTSPreference && !activity.getClass().getName().equals("com.baidu.tieba.LogoActivity")) {
                    startMainPreferenceActivity(activity);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Dialog.class, "dismissDialog", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Dialog dialog = (Dialog) param.thisObject;
                if (dialog.isShowing()) {
                    View view = dialog.getWindow().getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getRootView().getWindowToken(), 0);
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod("com.baidu.tieba.setting.more.MoreActivity", sClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                FrameLayout browseSetting = activity.findViewById(sClassLoader.loadClass("com.baidu.tieba.R$id").getField("browseSetting").getInt(null));
                LinearLayout parent = (LinearLayout) browseSetting.getParent();
                parent.addView(TSPreferenceHelper.createButton(sClassLoader, activity, "贴吧TS设置", null, v -> startMainPreferenceActivity(activity)), 11);
            }
        });
        Rule.findRule(new Rule.RuleCallBack() {
            @Override
            public void onRuleFound(String rule, String clazz, String method) {
                XposedHelpers.findAndHookMethod(clazz, sClassLoader, method, XposedHelpers.findClass("com.baidu.tieba.setting.im.more.SecretSettingActivity", sClassLoader), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.args[0];
                        Object navigationBar = Reflect.getObjectField(param.thisObject, "com.baidu.tbadk.core.view.NavigationBar");
                        Class<?> NavigationBar = sClassLoader.loadClass("com.baidu.tbadk.core.view.NavigationBar");
                        LinearLayout containerView = activity.findViewById(sClassLoader.loadClass("com.baidu.tieba.R$id").getField("container_view").getInt(null));
                        if (activity.getIntent().getBooleanExtra("showTSPreference", false)) {
                            Class<?> ControlAlign = sClassLoader.loadClass("com.baidu.tbadk.core.view.NavigationBar$ControlAlign");
                            NavigationBar.getDeclaredMethod("setTitleText", String.class).invoke(navigationBar, "贴吧TS设置");
                            for (Object HORIZONTAL_RIGHT : ControlAlign.getEnumConstants()) {
                                if (HORIZONTAL_RIGHT.toString().equals("HORIZONTAL_RIGHT")) {
                                    TextView textView = (TextView) NavigationBar.getDeclaredMethod("addTextButton", ControlAlign, String.class, View.OnClickListener.class)
                                            .invoke(navigationBar, HORIZONTAL_RIGHT, "重启", (View.OnClickListener) v -> {
                                                Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                                                if (intent != null) {
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                                    activity.startActivity(intent);
                                                }
                                                activity.finishAffinity();
                                                System.exit(0);
                                            });
                                    if (!DisplayHelper.isLightMode(activity)) {
                                        textView.setTextColor(Color.parseColor("#FFCBCBCC"));
                                    }
                                    break;
                                }
                            }
                            containerView.removeAllViews();
                            containerView.addView(createMainPreference(activity));
                        } else if (activity.getIntent().getBooleanExtra("showModifyTabPreference", false)) {
                            NavigationBar.getDeclaredMethod("setTitleText", String.class).invoke(navigationBar, "修改底栏");
                            containerView.removeAllViews();
                            containerView.addView(createModifyTabPreference(activity));
                        }
                    }
                });
            }
        }, "Lcom/baidu/tieba/R$id;->black_address_list:I");
    }

    private void startMainPreferenceActivity(Activity activity) {
        sIsShowTSPreference = false;
        if (!Preferences.getIsEULAAccepted()) {
            StringBuilder stringBuilder = new StringBuilder().append("本模块开源免费，不会主动发起网络请求，不会上传任何用户数据，旨在技术交流。请勿将本模块用于商业或非法用途，由此产生的后果与开发者无关。\n若您不同意此协议，请立即卸载本模块！无论您以何种形式或方式使用本模块，皆视为您已同意此协议！");
            if (BuildConfig.VERSION_NAME.contains("alpha") || BuildConfig.VERSION_NAME.contains("beta")) {
                stringBuilder.append("\n\n提示：您当前安装的是非正式版本，可能含有较多错误，如果您希望得到更稳定的使用体验，建议您安装正式版本。");
            }
            TbDialogBuilder bdalert = new TbDialogBuilder(sClassLoader, activity, "使用协议", stringBuilder.toString(), true, null);
            bdalert.setOnNoButtonClickListener(v -> {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_DELETE);
                Intent intentToResolve = TSPreferenceHelper.launchModuleIntent(activity);
                if (intentToResolve != null) {
                    intent.setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                } else {
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                }
                activity.startActivity(intent);
            });
            bdalert.setOnYesButtonClickListener(v -> {
                Preferences.setEULAAccepted();
                startMainPreferenceActivity(activity);
                bdalert.dismiss();
            });
            bdalert.show();
        } else {
            Intent intent = new Intent().setClassName(activity, "com.baidu.tieba.setting.im.more.SecretSettingActivity");
            intent.putExtra("showTSPreference", true);
            activity.startActivity(intent);
        }
    }

    @NotNull
    private LinearLayout createMainPreference(Activity activity) {
        TSPreferenceHelper.PreferenceLayout preferenceLayout = new TSPreferenceHelper.PreferenceLayout(activity);
        if (Preferences.getIsPurifyEnabled()) {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "轻车简从"));
        } else {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "净化界面"));
        }
        preferenceLayout.addView(TSPreferenceHelper.createButton(sClassLoader, activity, "修改底栏", null, v -> {
            Intent intent = new Intent().setClassName(activity, "com.baidu.tieba.setting.im.more.SecretSettingActivity");
            intent.putExtra("showModifyTabPreference", true);
            activity.startActivity(intent);
        }));
        if (Preferences.getIsPurifyEnabled()) {
            preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "真正的净化界面", "purify"));
        }
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "净化进吧", "purify_enter"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "净化我的", "purify_my"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "隐藏小红点", "red_tip"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "只推荐已关注的吧", "follow_filter"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "过滤首页推荐", "personalized_filter"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "过滤帖子回复", "content_filter"));
        if (Preferences.getIsPurifyEnabled()) {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "别出新意"));
        } else {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "增加功能"));
        }
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "进吧增加收藏、历史", "create_view"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "我的收藏增加搜索、吧名", "thread_store"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "浏览历史增加搜索", "history_cache"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "楼层回复增加查看主题贴", "new_sub"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "长按下载保存全部图片", "save_images"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "长按关注的人设置备注名", "my_attention"));
        if (Preferences.getIsPurifyEnabled()) {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "垂手可得"));
        } else {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "自动化"));
        }
        SwitchViewHolder autoSign = new SwitchViewHolder(sClassLoader, activity, sRes, "自动签到", "auto_sign");
        if (!Preferences.getIsAutoSignEnabled()) {
            autoSign.newSwitch.setOnClickListener(v -> {
                TbDialogBuilder bdalert = new TbDialogBuilder(sClassLoader, activity, "提示",
                        "这是一个需要网络请求并且有封号风险的功能，您需要自行承担使用此功能的风险，请谨慎使用！", true, null);
                bdalert.setOnNoButtonClickListener(v2 -> bdalert.dismiss());
                bdalert.setOnYesButtonClickListener(v2 -> {
                    Preferences.setAutoSignEnabled();
                    autoSign.turnOn();
                    bdalert.dismiss();
                });
                bdalert.show();
            });
            autoSign.bdSwitch.setOnTouchListener((v, event) -> false);
        }
        preferenceLayout.addView(autoSign);
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "自动打开一键签到", "open_sign"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "更新时清理缓存", "clean_dir"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "自动查看原图", "origin_src"));
        if (Preferences.getIsPurifyEnabled()) {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "奇怪怪"));
        } else {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "其它"));
        }
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "存储重定向", "storage_redirect"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "禁用帖子手势", "forbid_gesture"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "用夜间模式代替深色模式", "eyeshield_mode"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "用赞踩差数代替赞数", "agree_num"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "交换吧热门与最新", "frs_tab"));
        if (Preferences.getIsPurifyEnabled()) {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "关于就是关于"));
        } else {
            preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, "关于"));
        }
        preferenceLayout.addView(TSPreferenceHelper.createButton(sClassLoader, activity, "版本", BuildConfig.VERSION_NAME, v -> {
            Intent intentToResolve = TSPreferenceHelper.launchModuleIntent(activity);
            if (intentToResolve == null) return;
            Intent intent = new Intent(intentToResolve);
            List<ResolveInfo> ris = activity.getPackageManager().queryIntentActivities(intentToResolve, 0);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            intent.setClassName(ris.get(0).activityInfo.packageName, ris.get(0).activityInfo.name);
            activity.startActivity(intent);
        }));
        preferenceLayout.addView(TSPreferenceHelper.createButton(sClassLoader, activity, "源代码", "想要小星星", v -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            intent.setData(Uri.parse("https://github.com/GuhDoy/TiebaTS"));
            activity.startActivity(intent);
        }));
        preferenceLayout.addView(TSPreferenceHelper.createButton(sClassLoader, activity, "TG群", "及时获取更新", v -> {
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            intent.setData(Uri.parse("https://t.me/TabSwitch"));
            activity.startActivity(intent);
        }));
        preferenceLayout.addView(TSPreferenceHelper.createButton(sClassLoader, activity, "作者", "developed by GM", v -> {
            sCount++;
            if (sCount % 3 == 0) {
                Toast.makeText(activity, TSPreferenceHelper.randomToast(), Toast.LENGTH_SHORT).show();
            }
            if (sCount >= 10) {
                Preferences.setPurifyEnabled();
            }
        }));
        return preferenceLayout;
    }

    private LinearLayout createModifyTabPreference(Activity activity) {
        TSPreferenceHelper.PreferenceLayout preferenceLayout = new TSPreferenceHelper.PreferenceLayout(activity);
        preferenceLayout.addView(TSPreferenceHelper.createTextView(sClassLoader, activity, null));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "隐藏首页", "home_recommend"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "隐藏进吧", "enter_forum"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "隐藏频道", "new_category"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "隐藏消息", "my_message"));
        preferenceLayout.addView(new SwitchViewHolder(sClassLoader, activity, sRes, "隐藏我的", "mine"));
        return preferenceLayout;
    }
}
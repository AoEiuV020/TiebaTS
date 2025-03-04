package gm.tieba.tabswitch.hooker;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import gm.tieba.tabswitch.hooker.model.BaseHooker;
import gm.tieba.tabswitch.hooker.model.Hooker;
import gm.tieba.tabswitch.hooker.model.Preferences;
import gm.tieba.tabswitch.hooker.model.Rule;
import gm.tieba.tabswitch.hooker.model.TbDialogBuilder;
import gm.tieba.tabswitch.hooker.model.TbEditText;
import gm.tieba.tabswitch.util.Reflect;

public class MyAttention extends BaseHooker implements Hooker {
    public void hook() throws Throwable {
        Rule.findRule(new Rule.RuleCallBack() {
            @Override
            public void onRuleFound(String rule, String clazz, String method) {
                XposedHelpers.findAndHookMethod(clazz, sClassLoader, method, int.class, View.class, ViewGroup.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) Reflect.getObjectField(param.thisObject, "com.baidu.tieba.myAttentionAndFans.PersonListActivity");
                        View root = (View) param.getResult();
                        View itemView = root.findViewById(sClassLoader.loadClass("com.baidu.tieba.R$id").getField("item_view").getInt(null));
                        TextView textView = root.findViewById(sClassLoader.loadClass("com.baidu.tieba.R$id").getField("name").getInt(null));
                        View tailContainer = root.findViewById(sClassLoader.loadClass("com.baidu.tieba.R$id").getField("tail_container").getInt(null));
                        if (textView.getText() != null) {
                            String name = (String) textView.getText();
                            tailContainer.setTag(name);
                            itemView.setOnLongClickListener(v -> {
                                showNoteDialog(activity, (String) tailContainer.getTag());
                                return false;
                            });
                            if (Preferences.getNote(name) != null) {
                                textView.setText(String.format("%s(%s)", Preferences.getNote(name), name));
                            }
                        }
                    }
                });
            }
        }, "Lcom/baidu/tieba/R$layout;->person_list_item:I");
        XposedHelpers.findAndHookMethod("tbclient.User$Builder", sClassLoader, "build", boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String nameShow = (String) XposedHelpers.getObjectField(param.thisObject, "name_show");
                if (Preferences.getNote(nameShow) != null) {
                    XposedHelpers.setObjectField(param.thisObject, "name_show",
                            String.format("%s(%s)", Preferences.getNote(nameShow), nameShow));
                }
            }
        });
    }

    @SuppressLint("ApplySharedPref")
    private void showNoteDialog(Activity activity, String key) {
        EditText editText = new TbEditText(sClassLoader, activity, sRes);
        editText.setHint(Preferences.getNote(key));
        TbDialogBuilder bdAlert = new TbDialogBuilder(sClassLoader, activity, null, null, true, editText);
        bdAlert.setOnNoButtonClickListener(v -> bdAlert.dismiss());
        bdAlert.setOnYesButtonClickListener(v -> {
            SharedPreferences.Editor editor = Preferences.getTsNotesEditor();
            if (TextUtils.isEmpty(editText.getText())) editor.putString(key, null);
            else editor.putString(key, editText.getText().toString());
            editor.commit();
            activity.finish();
            activity.startActivity(activity.getIntent());
            bdAlert.dismiss();
        });
        bdAlert.show();
        bdAlert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        editText.setSingleLine();
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                bdAlert.getYesButton().performClick();
                return true;
            }
            return false;
        });
        editText.selectAll();
        editText.requestFocus();
    }
}
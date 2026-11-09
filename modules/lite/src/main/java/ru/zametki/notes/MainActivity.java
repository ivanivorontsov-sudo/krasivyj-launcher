package ru.zametki.notes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class MainActivity extends Activity {
    static final int BG = -791853;
    static final int CARD = -1041;
    static final int GOLD = -3631570;
    static final int INK = -13751774;
    static final int LINE = -1845320;
    static final int MATCH = -1;
    static final int SUB = -7700630;
    static final int WRAP = -2;
    BaseAdapter adapter;
    EditText bodyEt;
    TextView countView;
    float density;
    Note editing;
    TextView emptyView;
    boolean isNew;
    ListView listView;
    TextView pinBtn;
    SharedPreferences prefs;
    EditText titleEt;
    final ArrayList<Note> notes = new ArrayList<>();
    final ArrayList<Note> shown = new ArrayList<>();
    String query = "";
    final SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", new Locale("ru"));

    static class Note {
        long id;
        boolean pinned;
        long updated;
        String title = "";
        String body = "";

        Note() {
        }
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        // Android 13+ with enableOnBackInvokedCallback (and always on targetSdk 36): onBackPressed() is not called.
        if (android.os.Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { if (this.editing != null) closeEditor(); else finish(); });
        this.density = getResources().getDisplayMetrics().density;
        this.prefs = getSharedPreferences("mod_notes", 0);
        load();
        prepareWindow();
        showList();
    }

    void prepareWindow() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        androidx.core.view.WindowInsetsControllerCompat bars =
            androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(true);
        bars.setAppearanceLightNavigationBars(true);
    }

    void applyScreenInsets(View root) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
                    | androidx.core.view.WindowInsetsCompat.Type.displayCutout()
                    | androidx.core.view.WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(root);
    }

    int dp(int i) {
        return (int) ((i * this.density) + 0.5f);
    }

    GradientDrawable box(int i, int i2, int i3) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(i);
        gradientDrawable.setCornerRadius(dp(i2));
        if (i3 != 0) {
            gradientDrawable.setStroke(dp(1), i3);
        }
        return gradientDrawable;
    }

    TextView tv(String str, int i, int i2, boolean z) {
        TextView textView = new TextView(this);
        textView.setText(str);
        textView.setTextSize(i);
        textView.setTextColor(i2);
        if (z) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    TextView iconBtn(String str) {
        TextView tv = tv(str, 22, INK, false);
        tv.setGravity(17);
        tv.setMinWidth(dp(48));
        tv.setMinHeight(dp(48));
        return tv;
    }

    String plural(int i) {
        String str;
        int i2 = i % 10;
        int i3 = i % 100;
        if (i2 != 1 || i3 == 11) {
            str = (i2 < 2 || i2 > 4 || (i3 >= 12 && i3 <= 14)) ? "заметок" : "заметки";
        } else {
            str = "заметка";
        }
        return i + " " + str;
    }

    String firstLine(String str) {
        String trim = str.trim();
        int indexOf = trim.indexOf(10);
        return indexOf < 0 ? trim : trim.substring(0, indexOf).trim();
    }

    String afterFirstLine(String str) {
        String trim = str.trim();
        int indexOf = trim.indexOf(10);
        return indexOf < 0 ? "" : trim.substring(indexOf + 1).trim();
    }

    void hideKb() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            ((InputMethodManager) getSystemService("input_method")).hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    void load() {
        this.notes.clear();
        try {
            JSONArray jSONArray = new JSONArray(this.prefs.getString("data", "[]"));
            for (int i = 0; i < jSONArray.length(); i++) {
                JSONObject jSONObject = jSONArray.getJSONObject(i);
                Note note = new Note();
                note.id = jSONObject.optLong("id");
                note.title = jSONObject.optString("t", "");
                note.body = jSONObject.optString("b", "");
                note.updated = jSONObject.optLong("u");
                note.pinned = jSONObject.optBoolean("p", false);
                this.notes.add(note);
            }
        } catch (Exception e) {
        }
    }

    void save() {
        try {
            JSONArray jSONArray = new JSONArray();
            Iterator<Note> it = this.notes.iterator();
            while (it.hasNext()) {
                Note next = it.next();
                JSONObject jSONObject = new JSONObject();
                jSONObject.put("id", next.id);
                jSONObject.put("t", next.title);
                jSONObject.put("b", next.body);
                jSONObject.put("u", next.updated);
                jSONObject.put("p", next.pinned);
                jSONArray.put(jSONObject);
            }
            this.prefs.edit().putString("data", jSONArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось сохранить. Бывает.", 0).show();
        }
    }

    void showList() {
        this.editing = null;
        FrameLayout frameLayout = new FrameLayout(this);
        frameLayout.setBackgroundColor(BG);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        frameLayout.addView(linearLayout, new FrameLayout.LayoutParams(MATCH, MATCH));
        int titleSp = getResources().getConfiguration().fontScale > 1.15f ? 24 : 30;
        TextView tv = tv("Заметки", titleSp, INK, true);
        tv.setMaxLines(2);
        tv.setPadding(dp(20), dp(16), dp(20), 0);
        linearLayout.addView(tv, new LinearLayout.LayoutParams(MATCH, WRAP));
        this.countView = tv("", 13, SUB, false);
        this.countView.setMaxLines(3);
        this.countView.setPadding(dp(20), dp(2), dp(20), 0);
        linearLayout.addView(this.countView, new LinearLayout.LayoutParams(MATCH, WRAP));
        EditText editText = new EditText(this);
        editText.setSingleLine(true);
        editText.setHint("Поиск: куда же я это записал?..");
        editText.setHintTextColor(SUB);
        editText.setTextColor(INK);
        editText.setTextSize(15.0f);
        editText.setEllipsize(TextUtils.TruncateAt.END);
        editText.setBackground(box(CARD, 14, LINE));
        editText.setPadding(dp(16), dp(12), dp(16), dp(12));
        editText.setText(this.query);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH, WRAP);
        layoutParams.setMargins(dp(16), dp(12), dp(16), dp(6));
        linearLayout.addView(editText, layoutParams);
        editText.addTextChangedListener(new TextWatcher() { // from class: ru.zametki.notes.MainActivity.1
            @Override // android.text.TextWatcher
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override // android.text.TextWatcher
            public void afterTextChanged(Editable editable) {
                MainActivity.this.query = editable.toString();
                MainActivity.this.refresh();
            }
        });
        FrameLayout frameLayout2 = new FrameLayout(this);
        linearLayout.addView(frameLayout2, new LinearLayout.LayoutParams(MATCH, 0, 1.0f));
        this.listView = new ListView(this);
        this.listView.setDivider(null);
        this.listView.setDividerHeight(0);
        this.listView.setClipToPadding(false);
        this.listView.setPadding(0, dp(4), 0, dp(96));
        frameLayout2.addView(this.listView, new FrameLayout.LayoutParams(MATCH, MATCH));
        this.emptyView = tv("", 16, SUB, false);
        this.emptyView.setGravity(17);
        this.emptyView.setPadding(dp(32), dp(32), dp(32), dp(96));
        frameLayout2.addView(this.emptyView, new FrameLayout.LayoutParams(MATCH, MATCH));
        this.adapter = new BaseAdapter() { // from class: ru.zametki.notes.MainActivity.2
            @Override // android.widget.Adapter
            public int getCount() {
                return MainActivity.this.shown.size();
            }

            @Override // android.widget.Adapter
            public Object getItem(int i) {
                return MainActivity.this.shown.get(i);
            }

            @Override // android.widget.Adapter
            public long getItemId(int i) {
                return MainActivity.this.shown.get(i).id;
            }

            @Override // android.widget.Adapter
            public View getView(int i, View view, ViewGroup viewGroup) {
                return MainActivity.this.buildCard(MainActivity.this.shown.get(i));
            }
        };
        this.listView.setAdapter((ListAdapter) this.adapter);
        this.listView.setOnItemClickListener(new AdapterView.OnItemClickListener() { // from class: ru.zametki.notes.MainActivity.3
            @Override // android.widget.AdapterView.OnItemClickListener
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long j) {
                MainActivity.this.hideKb();
                MainActivity.this.openEditor(MainActivity.this.shown.get(i));
            }
        });
        this.listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { // from class: ru.zametki.notes.MainActivity.4
            @Override // android.widget.AdapterView.OnItemLongClickListener
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long j) {
                MainActivity.this.itemMenu(MainActivity.this.shown.get(i));
                return true;
            }
        });
        TextView tv2 = tv("+", 34, MATCH, false);
        tv2.setGravity(17);
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setShape(1);
        gradientDrawable.setColor(GOLD);
        tv2.setBackground(gradientDrawable);
        tv2.setElevation(dp(6));
        FrameLayout.LayoutParams layoutParams2 = new FrameLayout.LayoutParams(dp(60), dp(60));
        layoutParams2.gravity = 8388693;
        layoutParams2.setMargins(0, 0, dp(22), dp(24));
        frameLayout.addView(tv2, layoutParams2);
        tv2.setOnClickListener(new View.OnClickListener() { // from class: ru.zametki.notes.MainActivity.5
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.hideKb();
                MainActivity.this.openEditor(null);
            }
        });
        setContentView(frameLayout);
        applyScreenInsets(frameLayout);
        refresh();
    }

    View buildCard(Note note) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setPadding(dp(16), dp(6), dp(16), dp(6));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setBackground(box(CARD, 16, LINE));
        linearLayout2.setPadding(dp(16), dp(14), dp(16), dp(12));
        String firstLine = note.title.length() > 0 ? note.title : firstLine(note.body);
        if (firstLine.length() == 0) {
            firstLine = "Без названия";
        }
        if (note.pinned) {
            firstLine = "📌 " + firstLine;
        }
        TextView tv = tv(firstLine, 17, INK, true);
        tv.setMaxLines(2);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout2.addView(tv, new LinearLayout.LayoutParams(MATCH, WRAP));
        String trim = note.title.length() > 0 ? note.body.trim() : afterFirstLine(note.body);
        if (trim.length() > 0) {
            TextView tv2 = tv(trim, 14, SUB, false);
            tv2.setMaxLines(3);
            tv2.setEllipsize(TextUtils.TruncateAt.END);
            tv2.setPadding(0, dp(4), 0, 0);
            linearLayout2.addView(tv2, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        TextView tv3 = tv(this.fmt.format(new Date(note.updated)), 11, GOLD, true);
        tv3.setPadding(0, dp(8), 0, 0);
        linearLayout2.addView(tv3, new LinearLayout.LayoutParams(MATCH, WRAP));
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(MATCH, WRAP));
        return linearLayout;
    }

    void refresh() {
        String str;
        String str2;
        this.shown.clear();
        String lowerCase = this.query.trim().toLowerCase(Locale.getDefault());
        Iterator<Note> it = this.notes.iterator();
        while (it.hasNext()) {
            Note next = it.next();
            if (lowerCase.length() == 0 || next.title.toLowerCase(Locale.getDefault()).contains(lowerCase) || next.body.toLowerCase(Locale.getDefault()).contains(lowerCase)) {
                this.shown.add(next);
            }
        }
        Collections.sort(this.shown, new Comparator<Note>() { // from class: ru.zametki.notes.MainActivity.6
            @Override // java.util.Comparator
            public int compare(Note note, Note note2) {
                int i = 1;
                if (note.pinned != note2.pinned) {
                    if (note.pinned) {
                        return MainActivity.MATCH;
                    }
                    return 1;
                }
                if (note.updated == note2.updated) {
                    i = 0;
                } else if (note.updated >= note2.updated) {
                    i = MainActivity.MATCH;
                }
                return i;
            }
        });
        this.adapter.notifyDataSetChanged();
        TextView textView = this.countView;
        if (this.notes.size() == 0) {
            str = "Пока ни одной. Мозг пока справляется сам.";
        } else {
            str = plural(this.notes.size()) + ", и все под контролем";
        }
        textView.setText(str);
        if (this.shown.size() == 0) {
            this.emptyView.setVisibility(0);
            TextView textView2 = this.emptyView;
            if (this.query.trim().length() > 0) {
                str2 = "Ничего не нашлось.\nМожет, это тебе приснилось?";
            } else {
                str2 = "Тут пусто, как в холодильнике перед зарплатой.\n\nЖми «+» и записывай гениальные мысли, пока они не сбежали.";
            }
            textView2.setText(str2);
            return;
        }
        this.emptyView.setVisibility(8);
    }

    void itemMenu(final Note note) {
        String[] strArr = new String[3];
        strArr[0] = note.pinned ? "Открепить" : "Закрепить";
        strArr[1] = "Поделиться";
        strArr[2] = "Удалить";
        new AlertDialog.Builder(this).setItems(strArr, new DialogInterface.OnClickListener() { // from class: ru.zametki.notes.MainActivity.7
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                if (i == 0) {
                    note.pinned = note.pinned ? false : true;
                    MainActivity.this.save();
                    MainActivity.this.refresh();
                    return;
                }
                if (i == 1) {
                    MainActivity.this.share(note.title, note.body);
                } else {
                    MainActivity.this.confirmDelete(note, false);
                }
            }
        }).show();
    }

    void confirmDelete(final Note note, final boolean z) {
        new AlertDialog.Builder(this).setTitle("Удалить заметку?").setMessage("Она исчезнет навсегда — как носок в стиральной машине.").setPositiveButton("Удалить", new DialogInterface.OnClickListener() { // from class: ru.zametki.notes.MainActivity.8
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialogInterface, int i) {
                MainActivity.this.notes.remove(note);
                MainActivity.this.save();
                if (z) {
                    MainActivity.this.editing = null;
                    MainActivity.this.hideKb();
                    MainActivity.this.showList();
                    return;
                }
                MainActivity.this.refresh();
            }
        }).setNegativeButton("Оставить", (DialogInterface.OnClickListener) null).show();
    }

    void share(String str, String str2) {
        Intent intent = new Intent("android.intent.action.SEND");
        intent.setType("text/plain");
        if (str.length() > 0) {
            str2 = str + "\n\n" + str2;
        }
        intent.putExtra("android.intent.extra.TEXT", str2);
        startActivity(Intent.createChooser(intent, "Поделиться заметкой"));
    }

    void openEditor(Note note) {
        String str;
        this.isNew = note == null;
        if (this.isNew) {
            note = new Note();
        }
        this.editing = note;
        if (this.isNew) {
            this.editing.id = System.currentTimeMillis();
            this.editing.updated = this.editing.id;
        }
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setBackgroundColor(BG);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setGravity(16);
        linearLayout2.setPadding(dp(8), dp(4), dp(8), 0);
        TextView iconBtn = iconBtn("←");
        this.pinBtn = iconBtn("📌");
        TextView iconBtn2 = iconBtn("↗");
        TextView iconBtn3 = iconBtn("🗑");
        linearLayout2.addView(iconBtn);
        linearLayout2.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1.0f));
        linearLayout2.addView(this.pinBtn);
        linearLayout2.addView(iconBtn2);
        linearLayout2.addView(iconBtn3);
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(MATCH, WRAP));
        this.pinBtn.setAlpha(this.editing.pinned ? 1.0f : 0.35f);
        this.titleEt = new EditText(this);
        this.titleEt.setInputType(16385);
        this.titleEt.setHorizontallyScrolling(false);
        this.titleEt.setMaxLines(3);
        this.titleEt.setHint("Заголовок (или хотя бы намёк)");
        this.titleEt.setHintTextColor(SUB);
        this.titleEt.setTextColor(INK);
        this.titleEt.setTextSize(24.0f);
        this.titleEt.setTypeface(Typeface.DEFAULT_BOLD);
        this.titleEt.setBackground(null);
        this.titleEt.setPadding(dp(20), dp(8), dp(20), 0);
        this.titleEt.setText(this.editing.title);
        linearLayout.addView(this.titleEt, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (this.isNew) {
            str = "Новая заметка. Пока невинная.";
        } else {
            str = "Изменено: " + this.fmt.format(new Date(this.editing.updated));
        }
        TextView tv = tv(str, 12, GOLD, true);
        tv.setPadding(dp(20), dp(2), dp(20), dp(6));
        linearLayout.addView(tv, new LinearLayout.LayoutParams(MATCH, WRAP));
        this.bodyEt = new EditText(this);
        this.bodyEt.setInputType(147457);
        this.bodyEt.setGravity(8388659);
        this.bodyEt.setHint("Пиши сюда. Я никому не скажу — я приложение, у меня даже друзей нет.");
        this.bodyEt.setHintTextColor(SUB);
        this.bodyEt.setTextColor(INK);
        this.bodyEt.setTextSize(17.0f);
        this.bodyEt.setLineSpacing(0.0f, 1.2f);
        this.bodyEt.setBackground(null);
        this.bodyEt.setPadding(dp(20), dp(8), dp(20), dp(16));
        this.bodyEt.setText(this.editing.body);
        linearLayout.addView(this.bodyEt, new LinearLayout.LayoutParams(MATCH, 0, 1.0f));
        iconBtn.setOnClickListener(new View.OnClickListener() { // from class: ru.zametki.notes.MainActivity.9
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.closeEditor();
            }
        });
        this.pinBtn.setOnClickListener(new View.OnClickListener() { // from class: ru.zametki.notes.MainActivity.10
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.editing.pinned = !MainActivity.this.editing.pinned;
                MainActivity.this.pinBtn.setAlpha(MainActivity.this.editing.pinned ? 1.0f : 0.35f);
                Toast.makeText(MainActivity.this, MainActivity.this.editing.pinned ? "Закреплено. Теперь не потеряется." : "Откреплено. Лети, птичка.", 0).show();
            }
        });
        iconBtn2.setOnClickListener(new View.OnClickListener() { // from class: ru.zametki.notes.MainActivity.11
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                MainActivity.this.share(MainActivity.this.titleEt.getText().toString().trim(), MainActivity.this.bodyEt.getText().toString());
            }
        });
        iconBtn3.setOnClickListener(new View.OnClickListener() { // from class: ru.zametki.notes.MainActivity.12
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                if (MainActivity.this.isNew && !MainActivity.this.notes.contains(MainActivity.this.editing)) {
                    MainActivity.this.editing = null;
                    MainActivity.this.hideKb();
                    MainActivity.this.showList();
                    return;
                }
                MainActivity.this.confirmDelete(MainActivity.this.editing, true);
            }
        });
        setContentView(linearLayout);
        applyScreenInsets(linearLayout);
        if (this.isNew) {
            this.titleEt.requestFocus();
            this.titleEt.postDelayed(new Runnable() { // from class: ru.zametki.notes.MainActivity.13
                @Override // java.lang.Runnable
                public void run() {
                    if (MainActivity.this.titleEt != null) {
                        ((InputMethodManager) MainActivity.this.getSystemService("input_method")).showSoftInput(MainActivity.this.titleEt, 1);
                    }
                }
            }, 250L);
        }
    }

    void commitEditor(boolean z) {
        boolean z2 = true;
        if (this.editing != null && this.titleEt != null && this.bodyEt != null) {
            String trim = this.titleEt.getText().toString().trim();
            String editable = this.bodyEt.getText().toString();
            if (trim.length() == 0 && editable.trim().length() == 0) {
                boolean remove = this.notes.remove(this.editing);
                if (z && remove) {
                    Toast.makeText(this, "Пустую заметку не храню — я не коллекционер пустоты.", 0).show();
                }
            } else {
                if (!this.isNew && trim.equals(this.editing.title) && editable.equals(this.editing.body)) {
                    z2 = false;
                }
                this.editing.title = trim;
                this.editing.body = editable;
                if (z2) {
                    this.editing.updated = System.currentTimeMillis();
                }
                if (!this.notes.contains(this.editing)) {
                    this.notes.add(this.editing);
                }
                this.isNew = false;
            }
            save();
        }
    }

    void closeEditor() {
        commitEditor(true);
        hideKb();
        showList();
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        if (this.editing != null) {
            closeEditor();
        } else {
            super.onBackPressed();
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        if (this.editing != null) {
            commitEditor(false);
        }
        super.onPause();
    }
}

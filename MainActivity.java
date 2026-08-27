package com.senseultra.shizuku;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;
import android.os.IBinder;

public class MainActivity extends Activity {
    static final int REQ = 1001;
    TextView status, output;
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    IShellService shellService;
    Shizuku.UserServiceArgs userServiceArgs;

    final Shizuku.OnRequestPermissionResultListener permissionListener = (requestCode, grantResult) -> runOnUiThread(() -> {
        refresh();
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindShellService();
    });

    final ServiceConnection shellConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            shellService = IShellService.Stub.asInterface(service);
            runOnUiThread(() -> output.setText("Shizuku UserService conectado.\nUID=" + Shizuku.getUid()));
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            shellService = null;
            runOnUiThread(() -> status.setText("Shizuku UserService desconectado."));
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status); output = findViewById(R.id.output);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        refresh();
        findViewById(R.id.btnShizuku).setOnClickListener(v -> authorize());
        findViewById(R.id.btnInfo).setOnClickListener(v -> run("echo '=== DEVICE ==='; getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; echo '=== DISPLAY ==='; wm size; wm density; dumpsys display | grep -Ei 'DisplayDeviceInfo|DisplayInfo|mDisplayId|rotation|real |state=|refreshRate' | head -n 180"));
        findViewById(R.id.btnResolution).setOnClickListener(v -> resolutionDialog());
        findViewById(R.id.btnTouch).setOnClickListener(v -> run("echo '=== INPUT DEVICES ==='; dumpsys input | grep -E 'Device [0-9]+:|Sources:|Viewport|logicalFrame=|physicalFrame=|deviceSize=|orientation=' | head -n 260"));
        findViewById(R.id.btnAlignment).setOnClickListener(v -> run("echo '=== TOUCH GEOMETRY ==='; dumpsys input | grep -Ei 'TOUCHSCREEN|Viewport|logicalFrame=|physicalFrame=|deviceSize=|orientation=|surfaceOrientation|Motion Ranges|ABS_MT' | head -n 300; echo '=== DISPLAY ==='; dumpsys display | grep -Ei 'DisplayDeviceInfo|DisplayInfo|rotation|real ' | head -n 160"));
        findViewById(R.id.btnEvdev).setOnClickListener(v -> run("echo '=== GETEVENT DEVICES ==='; getevent -pl 2>/dev/null | head -n 320; echo '=== INPUT DEVICES ==='; dumpsys input | grep -Ei '/dev/input/event|name=|Sources:|Motion Ranges|ABS_MT|TOUCHSCREEN' | head -n 320"));
        findViewById(R.id.btnSensitivity).setOnClickListener(v -> sensitivityDialog());
        findViewById(R.id.btnFreeFire).setOnClickListener(v -> run("echo '=== FOREGROUND ==='; dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity|mFocusedApp' | head -n 40; echo '=== FREE FIRE ==='; pm path com.dts.freefireth 2>/dev/null || echo 'Free Fire normal: não instalado'; echo '=== FREE FIRE MAX ==='; pm path com.dts.freefiremax 2>/dev/null || echo 'Free Fire MAX: não instalado'"));
        findViewById(R.id.btnFps).setOnClickListener(v -> run("echo '=== DISPLAY MODES ==='; dumpsys display | grep -Ei 'refreshRate|fps|modeId|DisplayDeviceInfo' | head -n 220; echo '=== SURFACEFLINGER ==='; dumpsys SurfaceFlinger --display-id 0 2>/dev/null | grep -Ei 'fps|refresh|mode|active' | head -n 180"));
        findViewById(R.id.btnCache).setOnClickListener(v -> cacheDialog());
        findViewById(R.id.btnRestore).setOnClickListener(v -> restoreDialog());
        findViewById(R.id.btnShell).setOnClickListener(v -> shellDialog());
    }

    void refresh() {
        if (!Shizuku.pingBinder()) { status.setText("Shizuku: OFFLINE — abra o Shizuku e inicie o serviço."); return; }
        if (Build.VERSION.SDK_INT >= 23 && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { status.setText("Shizuku: conectado, mas este app ainda não foi autorizado."); return; }
        status.setText("Shizuku: ONLINE + AUTORIZADO | UID=" + Shizuku.getUid() + (Shizuku.getUid() == 2000 ? " (ADB/SHELL)" : ""));
        bindShellService();
    }

    void authorize() {
        try {
            if (!Shizuku.pingBinder()) { toast("Inicie o Shizuku primeiro."); return; }
            if (Build.VERSION.SDK_INT >= 23) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bindShellService();
                else Shizuku.requestPermission(REQ);
            } else bindShellService();
        } catch (Throwable e) { toast("Shizuku: " + e.getMessage()); }
    }

    boolean ready() {
        if (!Shizuku.pingBinder()) { toast("Shizuku não está ativo."); return false; }
        if (Build.VERSION.SDK_INT >= 23 && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { toast("Autorize o Sense Ultra no Shizuku."); return false; }
        if (shellService == null) { bindShellService(); toast("Conectando ao UserService… tente novamente em alguns segundos."); return false; }
        return true;
    }

    void bindShellService() {
        if (!Shizuku.pingBinder()) return;
        if (Build.VERSION.SDK_INT >= 23 && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
        if (shellService != null) return;
        try {
            userServiceArgs = new Shizuku.UserServiceArgs(
                    new ComponentName(getPackageName(), ShellUserService.class.getName()))
                    .daemon(false).processNameSuffix("shell").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
            Shizuku.bindUserService(userServiceArgs, shellConnection);
            status.setText("Shizuku: autorizado — conectando UserService…");
        } catch (Throwable e) {
            toast("Falha ao iniciar UserService: " + e);
        }
    }

    void run(String cmd) {
        if (!ready()) return;
        output.setText("Executando via Shizuku UserService…\n$ " + cmd);
        exec.submit(() -> {
            String r;
            try { r = shellService.exec(cmd); }
            catch (Throwable e) { r = "ERRO: " + e; }
            final String result = r;
            runOnUiThread(() -> output.setText(result));
        });
    }

    void resolutionDialog() {
        LinearLayout l = box(); EditText s = field("Resolução, ex.: 1920x863", false); EditText d = field("DPI, ex.: 440", true); l.addView(s); l.addView(d);
        new AlertDialog.Builder(this).setTitle("Resolução / DPI").setView(l)
            .setPositiveButton("APLICAR", (x,w) -> { String a=s.getText().toString().trim(), b=d.getText().toString().trim(); String c=""; if(!a.isEmpty()) c += "wm size " + a + "; "; if(!b.isEmpty()) c += "wm density " + b + "; "; c += "echo '--- READBACK ---'; wm size; wm density"; run(c); })
            .setNeutralButton("RESTAURAR", (x,w) -> run("wm size reset; wm density reset; echo '--- READBACK ---'; wm size; wm density"))
            .setNegativeButton("CANCELAR", null).show();
    }

    void restoreDialog() { new AlertDialog.Builder(this).setTitle("Restaurar sistema").setMessage("Restaura resolução e densidade para os valores padrão do Android. Não apaga dados dos jogos.").setPositiveButton("RESTAURAR", (d,w) -> run("wm size reset; wm density reset; settings delete system pointer_speed 2>/dev/null; echo 'RESTAURADO'; wm size; wm density")).setNegativeButton("CANCELAR", null).show(); }
    void sensitivityDialog() { LinearLayout l=box(); String[] hints={"Nome do perfil","Resolução","DPI mouse","DPI/GG","X normal","Y normal","X comutada","Y comutada","Observação"}; EditText[] e=new EditText[hints.length]; for(int i=0;i<hints.length;i++){e[i]=field(hints[i],i>=1&&i<=7);l.addView(e[i]);} new AlertDialog.Builder(this).setTitle("Perfil de sensibilidade").setView(l).setPositiveButton("SALVAR",(d,w)->{String name=e[0].getText().toString().trim();if(name.isEmpty())name="Perfil "+System.currentTimeMillis();StringBuilder z=new StringBuilder();for(EditText q:e)z.append(q.getText().toString().replace("|"," ")).append("|");getSharedPreferences("profiles",0).edit().putString(name,z.toString()).apply();output.setText("PERFIL SALVO\n\n"+z);}).setNeutralButton("LISTAR",(d,w)->listProfiles()).setNegativeButton("FECHAR",null).show(); }
    void listProfiles(){ Map<String,?> all=getSharedPreferences("profiles",0).getAll();StringBuilder s=new StringBuilder("PERFIS SALVOS\n\n");for(String k:all.keySet())s.append("• ").append(k).append("\n").append(all.get(k)).append("\n\n");output.setText(s.length()>14?s.toString():"Nenhum perfil salvo."); }
    void cacheDialog(){new AlertDialog.Builder(this).setTitle("Limpar cache").setMessage("Tenta liberar cache do sistema. O comando não deve apagar os dados pessoais dos jogos.").setPositiveButton("EXECUTAR",(d,w)->run("cmd package trim-caches 999999999999999999 2>&1; echo CACHE_COMMAND_FINISHED")).setNegativeButton("CANCELAR",null).show();}
    void shellDialog(){EditText e=field("Ex.: dumpsys input",false);e.setSingleLine(false);e.setMinLines(4);new AlertDialog.Builder(this).setTitle("Console Shizuku").setMessage("Comandos são executados como o UID disponível ao Shizuku. Use somente comandos que você entende.").setView(e).setPositiveButton("EXECUTAR",(d,w)->run(e.getText().toString())).setNegativeButton("CANCELAR",null).show();}
    EditText field(String hint,boolean number){EditText e=new EditText(this);e.setHint(hint);e.setPadding(16,8,16,8);if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    LinearLayout box(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(32,8,32,8);return l;}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){Shizuku.removeRequestPermissionResultListener(permissionListener);try{if(userServiceArgs!=null)Shizuku.unbindUserService(userServiceArgs,shellConnection,false);}catch(Throwable ignored){}exec.shutdownNow();super.onDestroy();}
}

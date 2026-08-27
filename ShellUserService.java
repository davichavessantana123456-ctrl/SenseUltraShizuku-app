package com.senseultra.shizuku;

import android.os.RemoteException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService. Runs with the UID provided by Shizuku (shell/root). */
public class ShellUserService extends IShellService.Stub {
    @Override public void destroy() { System.exit(0); }
    @Override public void exit() { destroy(); }

    @Override public String exec(String command) throws RemoteException {
        if (command == null) return "ERRO: comando nulo";
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(false).start();
            final Process p = process;
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outThread = new Thread(() -> read(p.getInputStream(), out), "sense-shell-out");
            Thread errThread = new Thread(() -> read(p.getErrorStream(), err), "sense-shell-err");
            outThread.start();
            errThread.start();
            if (!p.waitFor(120, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "ERRO: comando excedeu 120 segundos";
            }
            outThread.join(2000);
            errThread.join(2000);
            StringBuilder result = new StringBuilder(out);
            if (err.length() > 0) result.append("ERR: ").append(err);
            return result.toString();
        } catch (Throwable e) {
            return "ERRO: " + e;
        }
    }

    private static void read(InputStream in, StringBuilder target) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = r.readLine()) != null) target.append(line).append('\n');
        } catch (Throwable e) {
            target.append("ERR: ").append(e).append('\n');
        }
    }
}

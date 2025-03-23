package io.xpipe.app.util;

import io.xpipe.app.issue.ErrorEvent;
import io.xpipe.core.process.CommandBuilder;
import io.xpipe.core.process.OsType;
import io.xpipe.core.process.ShellControl;
import io.xpipe.core.store.FileKind;
import io.xpipe.core.store.FilePath;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class DesktopHelper {

    public static Path getDesktopDirectory() throws Exception {
        if (OsType.getLocal() == OsType.WINDOWS) {
            return Path.of(LocalShell.getLocalPowershell()
                    .executeSimpleStringCommand("[Environment]::GetFolderPath([Environment+SpecialFolder]::Desktop)"));
        } else if (OsType.getLocal() == OsType.LINUX) {
            try (var sc = LocalShell.getShell().start()) {
                var out = sc.command("xdg-user-dir DESKTOP").readStdoutIfPossible();
                if (out.isPresent()) {
                    return Path.of(out.get());
                }
            }
        }

        return Path.of(System.getProperty("user.home") + "/Desktop");
    }

    public static Path getDownloadsDirectory() throws Exception {
        if (OsType.getLocal() == OsType.WINDOWS) {
            return Path.of(LocalShell.getLocalPowershell()
                    .executeSimpleStringCommand(
                            "(New-Object -ComObject Shell.Application).NameSpace('shell:Downloads').Self.Path"));
        } else if (OsType.getLocal() == OsType.LINUX) {
            try (var sc = LocalShell.getShell().start()) {
                var out = sc.command("xdg-user-dir DOWNLOAD").readStdoutIfPossible();
                if (out.isPresent() && !out.get().isBlank()) {
                    return Path.of(out.get());
                }
            }
        }

        return Path.of(System.getProperty("user.home") + "/Downloads");
    }

    public static void browsePathRemote(ShellControl sc, FilePath path, FileKind kind) throws Exception {
        var d = sc.getShellDialect();
        switch (sc.getOsType()) {
            case OsType.Windows windows -> {
                // Explorer does not support single quotes, so use normal quotes
                if (kind == FileKind.DIRECTORY) {
                    sc.command(CommandBuilder.of().add("explorer").addQuoted(path.toString()))
                            .execute();
                } else {
                    sc.command(CommandBuilder.of().add("explorer", "/select,\"" + path.toString() + "\""))
                            .execute();
                }
            }
            case OsType.Linux linux -> {
                var action = kind == FileKind.DIRECTORY
                        ? "org.freedesktop.FileManager1.ShowFolders"
                        : "org.freedesktop.FileManager1.ShowItems";
                var dbus = String.format(
                        """
                                                 dbus-send --session --print-reply --dest=org.freedesktop.FileManager1 --type=method_call /org/freedesktop/FileManager1 %s array:string:"file://%s" string:""
                                                 """,
                        action, path);
                var success = sc.executeSimpleBooleanCommand(dbus);
                if (success) {
                    return;
                }

                sc.command(CommandBuilder.of()
                                .add("xdg-open")
                                .addFile(kind == FileKind.DIRECTORY ? path : path.getParent()))
                        .execute();
            }
            case OsType.MacOs macOs -> {
                sc.command(CommandBuilder.of()
                                .add("open")
                                .addIf(kind == FileKind.DIRECTORY, "-R")
                                .addFile(path))
                        .execute();
            }
            case OsType.Bsd bsd -> {}
            case OsType.Solaris solaris -> {}
        }
    }

    public static void browsePathLocal(Path file) {
        if (file == null) {
            return;
        }

        if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            return;
        }

        if (!Files.exists(file)) {
            return;
        }

        ThreadHelper.runAsync(() -> {
            try {
                Desktop.getDesktop().open(file.toFile());
            } catch (Exception e) {
                ErrorEvent.fromThrowable(e).expected().handle();
            }
        });
    }

    public static void browseFileInDirectory(Path file) {
        if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
            if (!Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                ErrorEvent.fromMessage("Desktop integration unable to open file " + file)
                        .expected()
                        .handle();
                return;
            }

            ThreadHelper.runAsync(() -> {
                try {
                    Desktop.getDesktop().open(file.getParent().toFile());
                } catch (Exception e) {
                    ErrorEvent.fromThrowable(e).expected().handle();
                }
            });
            return;
        }

        ThreadHelper.runAsync(() -> {
            try {
                Desktop.getDesktop().browseFileDirectory(file.toFile());
            } catch (Exception e) {
                ErrorEvent.fromThrowable(e).expected().handle();
            }
        });
    }
}

package com.keepalive.cloudphone.hook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "CloudPhoneKeepAlive";
    private static final String MODULE_PKG = "com.keepalive.cloudphone";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": system_server loaded, installing hooks...");

        safeHook("forceStopPackage", () -> hookForceStopPackage(lpparam));
        safeHook("killBackgroundProcesses", () -> hookKillBackgroundProcesses(lpparam));
        safeHook("killPackageProcessesLocked", () -> hookKillPackageProcessesLocked(lpparam));
        safeHook("removeProcessLocked", () -> hookRemoveProcessLocked(lpparam));
        safeHook("ProcessRecord.kill", () -> hookProcessRecordKill(lpparam));
        safeHook("OomAdj", () -> hookOomAdj(lpparam));
        safeHook("LowMemDetector", () -> hookLowMemDetector(lpparam));
        safeHook("AppOps", () -> hookAppOps(lpparam));
        safeHook("WakeLock/Alarm/Job", () -> hookSystemServices(lpparam));
        safeHook("ProcessRestart", () -> hookProcessRestart(lpparam));

        XposedBridge.log(TAG + ": all hooks installed");
    }

    private void safeHook(String name, Runnable hook) {
        try {
            hook.run();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook [" + name + "] failed: " + t.getMessage());
        }
    }

    private void hookForceStopPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> amsClass = XposedHelpers.findClass(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader);

        XC_MethodHook blockHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = (String) param.args[0];
                if (isProtectedApp(pkg)) {
                    XposedBridge.log(TAG + ": blocked forceStopPackage for " + pkg);
                    param.setResult(null);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(amsClass, "forceStopPackage",
                    String.class, int.class, int.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(amsClass, "forceStopPackage",
                    String.class, int.class, boolean.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(amsClass, "forceStopPackage",
                    String.class, int.class, blockHook);
        } catch (Throwable ignored) {
        }
    }

    private void hookKillBackgroundProcesses(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> amsClass = XposedHelpers.findClass(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader);

        XC_MethodHook blockHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = (String) param.args[0];
                if (isProtectedApp(pkg)) {
                    XposedBridge.log(TAG + ": blocked killBackgroundProcesses for " + pkg);
                    param.setResult(null);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(amsClass, "killBackgroundProcesses",
                    String.class, int.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(amsClass, "killBackgroundProcesses",
                    String.class, blockHook);
        } catch (Throwable ignored) {
        }
    }

    private void hookKillPackageProcessesLocked(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> amsClass = XposedHelpers.findClass(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader);

        XC_MethodHook blockHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] instanceof String) {
                    String pkg = (String) param.args[0];
                    if (isProtectedApp(pkg)) {
                        XposedBridge.log(TAG + ": blocked killPackageProcessesLocked for " + pkg);
                        param.setResult(null);
                    }
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(amsClass, "killPackageProcessesLocked",
                    String.class, int.class, int.class, boolean.class, String.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(amsClass, "killPackageProcessesLocked",
                    String.class, int.class, int.class, int.class, boolean.class,
                    boolean.class, boolean.class, String.class, blockHook);
        } catch (Throwable ignored) {
        }

        Class<?> processListClass = XposedHelpers.findClass(
                "com.android.server.am.ProcessList", lpparam.classLoader);
        try {
            XposedHelpers.findAndHookMethod(processListClass, "killPackageProcessesLocked",
                    String.class, int.class, int.class, boolean.class, String.class, blockHook);
        } catch (Throwable ignored) {
        }
    }

    private void hookRemoveProcessLocked(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> processListClass = XposedHelpers.findClass(
                "com.android.server.am.ProcessList", lpparam.classLoader);
        Class<?> processRecordClass = XposedHelpers.findClass(
                "com.android.server.am.ProcessRecord", lpparam.classLoader);

        XC_MethodHook blockHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                for (Object arg : param.args) {
                    if (arg != null && arg.getClass() == processRecordClass) {
                        String pkg = getProcessPkg(arg);
                        if (isProtectedApp(pkg)) {
                            XposedBridge.log(TAG + ": blocked removeProcessLocked for " + pkg);
                            param.setResult(false);
                            return;
                        }
                    }
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(processListClass, "removeProcessLocked",
                    processRecordClass, boolean.class, boolean.class, boolean.class, String.class,
                    blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(processListClass, "removeProcessLocked",
                    processRecordClass, boolean.class, boolean.class, String.class,
                    blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(processListClass, "removeProcessLocked",
                    processRecordClass, boolean.class, String.class,
                    blockHook);
        } catch (Throwable ignored) {
        }
    }

    private void hookProcessRecordKill(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> processRecordClass = XposedHelpers.findClass(
                "com.android.server.am.ProcessRecord", lpparam.classLoader);

        XC_MethodHook blockHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = getProcessPkg(param.thisObject);
                if (isProtectedApp(pkg)) {
                    String reason = param.args[0] instanceof String ? (String) param.args[0] : "unknown";
                    XposedBridge.log(TAG + ": blocked ProcessRecord.kill for " + pkg + " reason=" + reason);
                    param.setResult(null);
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(processRecordClass, "kill",
                    String.class, boolean.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(processRecordClass, "kill",
                    String.class, int.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(processRecordClass, "killLocked",
                    String.class, boolean.class, blockHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(processRecordClass, "killLocked",
                    String.class, int.class, blockHook);
        } catch (Throwable ignored) {
        }
    }

    private void hookOomAdj(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> processRecordClass = XposedHelpers.findClass(
                "com.android.server.am.ProcessRecord", lpparam.classLoader);

        XC_MethodHook adjHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String pkg = getProcessPkg(param.thisObject);
                if (isProtectedApp(pkg)) {
                    param.args[0] = 0;
                }
            }
        };

        for (String method : new String[]{"setCurAdj", "setCurRawAdj", "setSetAdj"}) {
            try {
                XposedHelpers.findAndHookMethod(processRecordClass, method, int.class, adjHook);
            } catch (Throwable ignored) {
            }
        }

        try {
            XposedHelpers.findAndHookMethod(processRecordClass, "setMaxAdj", int.class, adjHook);
        } catch (Throwable ignored) {
        }

        Class<?> amsClass = XposedHelpers.findClass(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader);

        XC_MethodHook computeAdjHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object processRecord = param.args[0];
                String pkg = getProcessPkg(processRecord);
                if (isProtectedApp(pkg)) {
                    param.setResult(0);
                }
            }
        };

        for (String method : new String[]{"computeOomAdjLSP", "computeOomAdj"}) {
            try {
                XposedHelpers.findAndHookMethod(amsClass, method,
                        processRecordClass, int.class, boolean.class,
                        processRecordClass, long.class, long.class,
                        computeAdjHook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(amsClass, method,
                        processRecordClass, int.class,
                        processRecordClass, long.class, long.class,
                        computeAdjHook);
            } catch (Throwable ignored) {
            }
        }

        XC_MethodHook applyAdjHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object processRecord = param.args[0];
                String pkg = getProcessPkg(processRecord);
                if (isProtectedApp(pkg)) {
                    try {
                        XposedHelpers.callMethod(processRecord, "setCurAdj", 0);
                        XposedHelpers.callMethod(processRecord, "setSetAdj", 0);
                    } catch (Throwable ignored) {
                    }
                }
            }
        };

        try {
            XposedHelpers.findAndHookMethod(amsClass, "applyOomAdjLocked",
                    processRecordClass, boolean.class, long.class, long.class,
                    applyAdjHook);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod(amsClass, "applyOomAdjLocked",
                    processRecordClass, boolean.class, long.class, long.class, long.class,
                    applyAdjHook);
        } catch (Throwable ignored) {
        }
    }

    private void hookLowMemDetector(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> lmkClass = XposedHelpers.findClass(
                    "com.android.server.am.LowMemDetector", lpparam.classLoader);
            Class<?> processRecordClass = XposedHelpers.findClass(
                    "com.android.server.am.ProcessRecord", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(lmkClass, "isAlive", processRecordClass,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = getProcessPkg(param.args[0]);
                            if (isProtectedApp(pkg)) {
                                param.setResult(true);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private void hookAppOps(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> appOpsClass = XposedHelpers.findClass(
                    "com.android.server.AppOpsService", lpparam.classLoader);

            XC_MethodHook runHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    for (int i = 0; i < param.args.length; i++) {
                        if (param.args[i] instanceof String && isProtectedApp((String) param.args[i])) {
                            param.setResult(0);
                            return;
                        }
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(appOpsClass, "noteOperation",
                        int.class, int.class, String.class, String.class, boolean.class, runHook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(appOpsClass, "noteOperation",
                        int.class, int.class, String.class, runHook);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookSystemServices(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> pmsClass = XposedHelpers.findClass(
                    "com.android.server.power.PowerManagerService", lpparam.classLoader);

            try {
                XposedHelpers.findAndHookMethod(pmsClass, "acquireWakeLockInternal",
                        long.class, String.class, int.class, String.class,
                        android.os.IBinder.class, String.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String pkg = (String) param.args[1];
                                if (isProtectedApp(pkg)) {
                                    param.args[0] = Long.MAX_VALUE;
                                }
                            }
                        });
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(pmsClass, "acquireWakeLockInternal",
                        long.class, String.class, int.class, String.class,
                        android.os.IBinder.class, String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (param.args[1] instanceof String && isProtectedApp((String) param.args[1])) {
                                    param.args[0] = Long.MAX_VALUE;
                                }
                            }
                        });
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> alarmClass = XposedHelpers.findClass(
                    "com.android.server.alarm.AlarmManagerService", lpparam.classLoader);

            XC_MethodHook blockAlarmHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    for (Object arg : param.args) {
                        if (arg instanceof String && isProtectedApp((String) arg)) {
                            param.setResult(null);
                            return;
                        }
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(alarmClass, "remove",
                        String.class, android.os.IBinder.class, blockAlarmHook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(alarmClass, "removeLocked",
                        String.class, android.os.IBinder.class, blockAlarmHook);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> jobClass = XposedHelpers.findClass(
                    "com.android.server.job.JobSchedulerService", lpparam.classLoader);

            XC_MethodHook blockJobHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    for (Object arg : param.args) {
                        if (arg instanceof String && isProtectedApp((String) arg)) {
                            param.setResult(null);
                            return;
                        }
                    }
                }
            };

            try {
                XposedHelpers.findAndHookMethod(jobClass, "cancelAllJobsForPackage",
                        String.class, int.class, blockJobHook);
            } catch (Throwable ignored) {
            }
            try {
                XposedHelpers.findAndHookMethod(jobClass, "cancelAllJobsForPackage",
                        String.class, blockJobHook);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookProcessRestart(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> amsClass = XposedHelpers.findClass(
                "com.android.server.am.ActivityManagerService", lpparam.classLoader);

        try {
            XposedHelpers.findAndHookMethod(amsClass, "startProcessLocked",
                    String.class, String.class, boolean.class, int.class,
                    boolean.class, boolean.class, boolean.class,
                    android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (isProtectedApp(pkg) && param.getResult() == null) {
                                XposedBridge.log(TAG + ": startProcessLocked failed for " + pkg);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> processRecordClass = XposedHelpers.findClass(
                    "com.android.server.am.ProcessRecord", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(amsClass, "addAppLocked",
                    processRecordClass, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String pkg = getProcessPkg(param.args[0]);
                            if (isProtectedApp(pkg)) {
                                XposedBridge.log(TAG + ": addAppLocked for " + pkg);
                            }
                        }
                    });
        } catch (Throwable ignored) {
        }
    }

    private String getProcessPkg(Object processRecord) {
        if (processRecord == null) return null;
        try {
            Object info = XposedHelpers.getObjectField(processRecord, "info");
            if (info != null) {
                return (String) XposedHelpers.callMethod(info, "getPackageName");
            }
        } catch (Throwable ignored) {
        }
        try {
            return (String) XposedHelpers.getObjectField(processRecord, "processName");
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isProtectedApp(String pkg) {
        return MODULE_PKG.equals(pkg);
    }
}

/*
 __  __                  _               _               _ 
|  \/  | ___  __ _  __ _| |__   __ _ ___| |_ ___ _ __ __| |
| |\/| |/ _ \/ _` |/ _` | '_ \ / _` / __| __/ _ \ '__/ _` |
| |  | |  __/ (_| | (_| | |_) | (_| \__ \ ||  __/ | | (_| |
|_|  |_|\___|\__, |\__,_|_.__/ \__,_|___/\__\___|_|  \__,_|
             |___/                                         
© Perpetrated by tonikelope since 2016
 */
package com.tonikelope.megabasterd;

import java.util.logging.Level;
import static java.util.logging.Level.SEVERE;
import java.util.logging.Logger;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ProgressMeter implements Runnable, SecureSingleThreadNotifiable {

    private static final Logger LOG = Logger.getLogger(ProgressMeter.class.getName());
    private final Transference _transference;
    private volatile boolean _exit;
    private final Object _secure_notify_lock;
    private volatile boolean _notified;
    private long _progress;
    
    private Timer progressTimer;
    private static final int PROGRESS_UPDATE_INTERVAL = 500;

    public ProgressMeter(Transference transference) {
        _notified = false;
        _secure_notify_lock = new Object();
        _transference = transference;
        _progress = 0;
        _exit = false;
    }

    public void setExit(boolean value) {
        _exit = value;
    }

    @Override
    public void secureNotify() {
        synchronized (_secure_notify_lock) {
            _notified = true;
            _secure_notify_lock.notify();
        }
    }

    @Override
    public void secureWait() {
        synchronized (_secure_notify_lock) {
            while (!_notified) {
                try {
                    _secure_notify_lock.wait(1000);
                } catch (InterruptedException ex) {
                    _exit = true;
                    LOG.log(SEVERE, null, ex);
                }
            }
            _notified = false;
        }
    }

    @Override
    public void run() {
        LOG.log(Level.INFO, "{0} ProgressMeter hello! {1}",
                new Object[]{Thread.currentThread().getName(), _transference.getFile_name()});

        _progress = _transference.getProgress();

        // start timer on the edt
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressTimer = new Timer(PROGRESS_UPDATE_INTERVAL, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Long reads;
                        while ((reads = _transference.getPartialProgress().poll()) != null) {
                            _progress += reads;
                            _transference.setProgress(_progress);
                        }
                        if (_exit && _transference.getPartialProgress().isEmpty()) ((Timer) e.getSource()).stop();
                    }
                });
                progressTimer.start();
            }
        });

        while (!_exit) secureWait();

        LOG.log(Level.INFO, "{0} ProgressMeter bye bye! {1}",
            new Object[]{Thread.currentThread().getName(), _transference.getFile_name()});
    }
}
/*
 * Copyright (C) 2015 Sergej Shafarenka, halfbit.de
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.halfbit.rxandroid.permissions;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import rx.Observable;
import rx.Subscriber;
import rx.functions.FuncN;
import rx.subjects.PublishSubject;

/**
 * Android runtime permissions as rx observable.
 */
public class RxPermissions {

    private static RxPermissions INSTANCE = null;

    /**
     * Returns singleton instance of this class.
     *
     * @param context context used for checking whether permissions are granted or not.
     * @return the singleton instance
     */
    public static synchronized RxPermissions get(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new RxPermissions(context.getApplicationContext());
        }
        return INSTANCE;
    }

    /**
     * Instance of this class is a bridge between <code>RxObservable</code> instance and
     * an activity or a fragment.
     * <p>
     * When  <code>RxObservable</code> decides to request
     * permissions, it calls {@link #performRequestPermissions(String[])} method, which has
     * to forward the call to corresponding method of activity or fragment.
     * <p>
     * When there is a response, you should call {@link #onRequestPermissionsResult(String[], int[])}
     * method back to let <code>RxObservable</code> process the result.
     */
    public static abstract class PermissionsRequester {

        RxPermissions rxPermissions;
        String[] permissions;
        boolean resultDelivered;

        public abstract void performRequestPermissions(String[] permissions);

        @MainThread public void onRequestPermissionsResult(@NonNull String[] permissions,
                                                           @NonNull int[] grantResults) {
            assertInitialized(rxPermissions);
            HashMap<String, PublishSubject<Boolean>> subjects = rxPermissions.subjects;
            for (int size = permissions.length, i = 0; i < size; i++) {
                PublishSubject<Boolean> subject = subjects.get(permissions[i]);
                assertInitialized(subject);
                subject.onNext(grantResults[i] == PackageManager.PERMISSION_GRANTED);
            }
            resultDelivered = true;
        }

        @MainThread public void request() {
            assertInitialized(rxPermissions);
            List<String> notGrantedPermissions = null;
            for (String permission : permissions) {
                if (!rxPermissions.isGranted(permission)) {
                    if (notGrantedPermissions == null) {
                        notGrantedPermissions = new ArrayList<>(permissions.length);
                    }
                    notGrantedPermissions.add(permission);
                }
            }
            if (notGrantedPermissions != null) {
                String[] requestPermissions = new String[notGrantedPermissions.size()];
                requestPermissions = notGrantedPermissions.toArray(requestPermissions);
                performRequestPermissions(requestPermissions);
            }
        }
    }

    protected final Context context;
    protected final HashMap<String, PublishSubject<Boolean>> subjects;

    protected RxPermissions(Context context) {
        this.context = context;
        subjects = new HashMap<>(6);
    }

    /**
     * Returns never completing observable for given permissions. Observable values get emitted
     * every time user grants or denies permissions. Use {@link Observable#distinctUntilChanged()}
     * is you need distinct values.
     * <p>
     * Don't forget to unsubscribe, when you are not interested in the events anymore.
     *
     * @param permissions list of observed permissions
     * @return never completing observable for given permissions
     */
    @MainThread @NonNull public Observable<Boolean> observe(String... permissions) {
        List<Observable<Boolean>> observables = new ArrayList<>(permissions.length);
        for (String permission : permissions) {
            PublishSubject<Boolean> subj = subjects.get(permission);
            if (subj == null) {
                subj = PublishSubject.create();
                subjects.put(permission, subj);
            }
            observables.add(subj.startWith(isGranted(permission)));
        }
        return Observable.combineLatest(observables, RESULT_CHECKER);
    }

    /**
     * Returns observable for given permissions. It there is not granted permissions,
     * this method will call requester instance back to request permissions from the system.
     * Once all permissions are granted, returned observable completes.
     *
     * @param requester   instance requesting permissions from the system and receive
     *                    permissions result back
     * @param permissions list of requested permissions
     * @return observable for given permissions completing when all permissions are granted
     */
    @MainThread @NonNull public Observable<Boolean> request(
            @NonNull PermissionsRequester requester, String... permissions) {

        requester.rxPermissions = this;
        requester.permissions = permissions;
        requester.resultDelivered = false;

        List<Observable<Boolean>> observables = new ArrayList<>(permissions.length);
        for (String permission : permissions) {
            PublishSubject<Boolean> subj = subjects.get(permission);
            if (subj == null) {
                subj = PublishSubject.create();
                subjects.put(permission, subj);
            }
        }

        requester.request();

        for (String permission : permissions) {
            PublishSubject<Boolean> subj = subjects.get(permission);
            Observable<Boolean> observable = subj.asObservable();
            if (isGranted(permission)) {
                observable = observable.startWith(true);
            } else {
                if (requester.resultDelivered) {
                    observable = observable.startWith(false);
                }
            }
            observables.add(observable);
        }

        return Observable.combineLatest(observables, RESULT_CHECKER)
                .lift(new Observable.Operator<Boolean, Boolean>() {
                    @Override
                    public Subscriber<? super Boolean> call(Subscriber<? super Boolean> subscriber) {
                        CompletingOnTrueSubscriber s = new CompletingOnTrueSubscriber(subscriber);
                        subscriber.add(s);
                        return s;
                    }
                });
    }

    protected boolean isGranted(String permission) {
        return Build.VERSION.SDK_INT < 23 /*M*/ || isGranted60(permission);
    }

    @TargetApi(Build.VERSION_CODES.M) private boolean isGranted60(String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static void assertInitialized(Object obj) {
        if (obj == null) {
            throw new IllegalStateException("Make sure you request permissions by calling " +
                    "RxPermissions.request(...) method first.");
        }
    }

    private static FuncN<Boolean> RESULT_CHECKER = new FuncN<Boolean>() {
        @Override public Boolean call(Object... results) {
            for (Object result : results) {
                if (!((Boolean) result)) {
                    return false;
                }
            }
            return true;
        }
    };

    private static class CompletingOnTrueSubscriber extends Subscriber<Boolean> {
        private Subscriber<? super Boolean> subscriber;

        public CompletingOnTrueSubscriber(Subscriber<? super Boolean> subscriber) {
            this.subscriber = subscriber;
        }

        @Override public void onCompleted() { subscriber.onCompleted(); }

        @Override public void onError(Throwable e) { subscriber.onError(e); }

        @Override public void onNext(Boolean value) {
            subscriber.onNext(value);
            if (value) {
                subscriber.onCompleted();
            }
        }
    }

}

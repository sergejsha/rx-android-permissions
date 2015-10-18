package de.halfbit.rxandroid.permissions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.MainThread;
import android.test.AndroidTestCase;
import android.test.UiThreadTest;

import java.util.ArrayList;

import rx.Subscription;
import rx.observers.TestSubscriber;
import rx.subjects.PublishSubject;

public class RxPermissionsTest extends AndroidTestCase {

    @UiThreadTest
    public void testObserveGrantedPermissions() {

        TestSubscriber<Boolean> subscriber = new TestSubscriber<>();
        RxPermissionsUnderTest permissions = new RxPermissionsUnderTest(getContext(), true);

        Subscription subscription = permissions.observe(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(subscriber);

        assertEquals(2, permissions.requestedPermissions.size());
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE,
                permissions.requestedPermissions.get(0));
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                permissions.requestedPermissions.get(1));

        subscriber.assertNoErrors();
        subscriber.assertValue(true);
        subscriber.assertNotCompleted();

        subscription.unsubscribe();
    }

    @UiThreadTest
    public void testObserveNotGrantedThenGrantedPermissions() {

        TestSubscriber<Boolean> subscriber = new TestSubscriber<>();
        RxPermissionsUnderTest permissions = new RxPermissionsUnderTest(getContext(), false);

        Subscription subscription = permissions.observe(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(subscriber);

        subscriber.assertValues(false);

        permissions.setAllowed(Manifest.permission.READ_EXTERNAL_STORAGE);
        subscriber.assertValues(false, false);

        permissions.setAllowed(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        subscriber.assertValues(false, false, true);

        subscriber.assertNoErrors();
        subscriber.assertNotCompleted();

        subscription.unsubscribe();
    }

    @UiThreadTest
    public void testRequestGrantedPermissions() {

        TestSubscriber<Boolean> subscriber = new TestSubscriber<>();
        RxPermissionsUnderTest permissions = new RxPermissionsUnderTest(getContext(), true);
        MockPermissionsRequester requester = new MockPermissionsRequester(true);

        Subscription subscription = permissions.request(requester,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(subscriber);

        subscriber.assertValue(true);
        subscriber.assertNoErrors();
        subscriber.assertCompleted();

        assertTrue(subscription.isUnsubscribed());
    }

    public void testRequestNotGrantedPermissions() {

        TestSubscriber<Boolean> subscriber = new TestSubscriber<>();
        RxPermissionsUnderTest permissions = new RxPermissionsUnderTest(getContext(), false);
        MockPermissionsRequester requester = new MockPermissionsRequester(false);

        Subscription subscription = permissions.request(requester,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(subscriber);

        subscriber.assertValue(false);
        subscriber.assertNoErrors();
        subscriber.assertNotCompleted();

        assertFalse(subscription.isUnsubscribed());
    }

    public void testRequestNotGrantedThenGrantedPermissions() {

        TestSubscriber<Boolean> subscriber = new TestSubscriber<>();
        RxPermissionsUnderTest permissions = new RxPermissionsUnderTest(getContext(), false);
        MockPermissionsRequester requester = new MockPermissionsRequester(false);

        Subscription subscription = permissions.request(requester,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(subscriber);

        requester.granted = true;
        requester.request();

        subscriber.assertValues(false, false, true);
        subscriber.assertNoErrors();
        subscriber.assertCompleted();

        assertTrue(subscription.isUnsubscribed());
    }

    public void testObserveAndRequestPermissions() {

        TestSubscriber<Boolean> observeSubscriber = new TestSubscriber<>();
        TestSubscriber<Boolean> requestSubscriber = new TestSubscriber<>();

        RxPermissionsUnderTest permissions = new RxPermissionsUnderTest(getContext(), false);
        MockPermissionsRequester requester = new MockPermissionsRequester(false);

        permissions.observe(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(observeSubscriber);

        permissions.request(requester,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .subscribe(requestSubscriber);

        requester.granted = true;
        requester.request();

        observeSubscriber.assertValues(false, false, false, false, true);
        observeSubscriber.assertNoErrors();
        observeSubscriber.assertNotCompleted();

        requestSubscriber.assertValues(false, false, true);
        requestSubscriber.assertNoErrors();
        requestSubscriber.assertCompleted();
    }

    private static class RxPermissionsUnderTest extends RxPermissions {

        final ArrayList<String> requestedPermissions = new ArrayList<>(5);
        final boolean granted;

        private RxPermissionsUnderTest(Context context, boolean granted) {
            super(context);
            this.granted = granted;
        }

        @Override protected boolean isGranted(String permission) {
            requestedPermissions.add(permission);
            return granted;
        }

        public void setAllowed(String permission) {
            PublishSubject<Boolean> subj = subjects.get(permission);
            if (subj != null) {
                subj.onNext(true);
            }
        }
    }

    private static class MockPermissionsRequester extends RxPermissions.PermissionsRequester {

        final ArrayList<String> requestedPermissions;
        boolean granted;

        private MockPermissionsRequester(boolean granted) {
            this.requestedPermissions = new ArrayList<>();
            this.granted = granted;
        }

        @MainThread
        @Override public void performRequestPermissions(final String[] permissions) {
            final int[] result = new int[permissions.length];
            for (int i = 0, size = result.length; i < size; i++) {
                result[i] = granted ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED;
            }

            onRequestPermissionsResult(permissions, result);
        }
    }

}

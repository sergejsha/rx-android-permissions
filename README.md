# rx-android-permissions
Simple RxJava library for observing and requesting Android runtime permissions introduced in Android 6.0.

# Observing permissions

Sometimes it makes sense to ask user for absolutely required permissions in a separate onboarding step. Simple code down below shows either main or onboarding fragment depending on permissions granted, **without** asking user for those permissions. This is pure observe case.

    public class MainActivity extends AppCompatActivity {

        @Override public void onStart() {
            super.onStart();
            
            RxPermissions.get(this)
                  .observe(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                           Manifest.permission.READ_EXTERNAL_STORAGE)
                  .subscribe(granted -> {
                      if (granted) {
                          // you can pass to main fragment
                      } else {
                          // you can open onboarding fragment 
                      }
                  });
        }
    }

Note: Returned observable does never complete, so be sure to unsubscribe properly.

# Requesting permissions

Requesting permissions requires additional binding to component (an activity or a fragment) which can ask user for permissions and receives result. Then you can request permissions. Here is how you do it.

    public class OnboardingFragment extends Fragment {
        private static final int REQ_PERMISSIONS = 101;
    
        private final PermissionsRequester mPermissionsRequester = new PermissionsRequester() {
            @Override public void performRequestPermissions(String[] permissions) {
                // forward request to the system by calling fragment's method
                requestPermissions(permissions, REQ_PERMISSIONS);
            }
        };
        
        @Override
        public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
            if (requestCode == REQ_PERMISSIONS) {
                // forward response back to requester class for further processing
                mPermissionsRequester.onRequestPermissionsResult(permissions, grantResults);
            }
        }
        
        @Override public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            
            RxPermissions.get(getActivity())
                .request(mPermissionsRequester, 
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE)
                .subscribe(granted -> {
                    if (granted) {
                        // permissions were granted, observable completes
                    } else {
                        // user denied request
                    }
                });
        }
        
        public void onButtonClicked(View view) {
            mPermissionsRequester.request();
        }
    }

Observable returned by `RxPermissions.request()` method does only complete when all requested permissions are granted. Until then it emitts `false` every time user denies a permission request. You can show user a better explanation of why the app needs these permissions and ask for permissions again. Use `PermissionsRequester.request()` method for triggering permissions request dialog once again.










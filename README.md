Simple RxJava library for observing and requesting Android runtime permissions introduced in Android 6.0.

# Observing permissions

Sometimes it makes sense to ask user for absolutely required permissions in a separate onboarding step. Simple code down below shows either main or onboarding fragment depending on permissions granted, **without** asking user for those permissions. This is pure observe case.

```java
public class MainActivity extends AppCompatActivity {

    @Override public void onStart() {
        super.onStart();
        
        mSubscription = RxPermissions.get(this)
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
    
    @Override public void onStop() {
        mSubsrciption.unsubsribe();
        mSubsrciption = null;
        super.onStop();
    }
}

```

Note: Returned observable does never complete, so be sure to unsubscribe properly.

# Requesting permissions

Requesting permissions requires additional binding to component (an activity or a fragment) which can ask user for permissions and receives result. Then you can request permissions. Here is how you do it.

```java
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
        
        mSubscription = RxPermissions.get(getActivity())
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
    
    @Override public void onDestroyView() {
        mSubsrciption.unsubsribe();
        mSubsrciption = null;
        super.onDestroyView();
    }
    
    public void onButtonClicked(View view) {
        mPermissionsRequester.request();
    }
}
```

Observable returned by `RxPermissions.request()` method does only complete when all requested permissions are granted. Until then it emitts `false` every time user denies a permission request. You can show user a better explanation of why the app needs these permissions and ask for permissions again. Use `PermissionsRequester.request()` method for triggering permissions request dialog once again.

# Tests & stability
Although this library has unit tests covering whole functionality described above, there is still no productive apps using it yet.

# Credits
This library was inspired by https://github.com/tbruyelle/RxPermissions, but it uses a bit different design allowing pure observation of permissions and follow up permissions request without necessity to create new observable.

# License

    Copyright (c) 2015, 2016 Sergej Shafarenka, halfbit.de

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

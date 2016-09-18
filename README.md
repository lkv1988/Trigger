[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Trigger-brightgreen.svg?style=flat)](http://android-arsenal.com/details/1/1761)

What is Trigger
===

Do you know JobScheduler which is new added in Android Lollipop version? It is a job scheduler as its name. Define your own job, which need some conditions to happen, suit it up with such as device charging or unmeterred network(just like wifi, what else we have?) or device idle status, then throw it to the JobScheduler framework, your job will happen while all these conditions are satisfied. Awesome feature, isn't it? But a pity is it's only available above API 21. So I try my best to copy it to API 14 and named it Trigger.

###Can Trigger do what JobScheduler do?

Good question! In my opinion and according to the test result, it do CAN, and I think Trigger can do more.

###Features

- support multi conditions combination with one job
- inner conditions: device charging, unmeterred network and idle status
- support persist job, means that persist-able job can be triggered after device reboot
- support job's deadline, last chance to be triggered
- allow your job's action runs in background or main thread, follow your configuration

While all your job's conditions are satisfied, your job's action just like a Duang~~~ Happened!

![duang](http://ww2.sinaimg.cn/large/e47e16abjw1epo63zm4u6g209205d7bs.gif)

###How does Trigger work?

There is a Android service component running in background named TriggerLoop, its duty is managing all jobs you scheduled, updating their status, checking them if they can be triggered, handling remove request from user, and even recovering them from service restarting or device rebooting. Do not forget declare it in your AndroidManifest.xml, otherwise Trigger can not start at all.

Usage
---

###Support Api Level

Android ICS (API14)

###Include in your project

- Maven
    
    ```
    <dependency>
        <groupId>com.github.airk000</groupId>
        <artifactId>trigger</artifactId>
        <version>1.1.0</version>
    </dependency>
    ```
- Gradle

    ```
    compile 'com.github.airk000:trigger:1.1.0'
    ```
    
###Declare

You should add these code in your application's `AndroidManifest.xml`, first is permissions:


```
<!-- inner status controller: network controller will need it -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<!-- wakelock helps Trigger work well, if you really don't like it, remove it, doesn't matter -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<!-- that's the point why Trigger can recover persist-able job after device rebooting, if you don't need it, never mind -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

then components:


```
<service android:name="com.github.airk.trigger.TriggerLoop" />
<!-- as what I said in permission, if you don't need it, remove it -->
<receiver android:name="com.github.airk.trigger.PersistReceiver">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```


###Schedule a Job

All operations should through Trigger instance, you can get it by using following code:

```
Trigger trigger = Trigger.getInstance(context);
```
It is in singleton design, so you will always get the same instance wherever invoke this.

Then new a simple and un-persist-able job and schedule it:

```
Job job = new Job(new Action() {
        @Override
        protected void act() {
            //do something
        }
    }).withExtra(new Condition() {
        @Override
        public String[] getAction() {
            return new String[]{YOUR_BROARCAST};
        }
    });
trigger.schedule(job);
```

After all these, while you sending YOUR_BROADCAST broadcast wherever you want, the job's action(//do something... there) will be triggered in background thread (want main thread? see later).

####More configurations

- multi constructors can give you chances to give your job a tag, or mark it want be persist
- different from Action class, ContextAction give you a chance to do something with a context (it is the TriggerLoop)
- `withExtra` let you add more conditions to your job
- `repeat` make the job can be triggered repeatability, and `repeat(long ms)` version give it a chance to not trigger too often
- `deadline` setup the deadline of the job, it's in RTC
- `attachOn` MAIN and BACKGROUND, MAIN means the job's action will be triggered in main thread, default is BACKGROUND

####Inner conditions

- `networkType` limit your job can be triggered in which network environment
- `needCharging` limit your job can only be triggered while device is in charing status
- `needDeviceIdle` limit your job can only be triggered while device is idle

>PS: How to define the idle status? After your device drop into daydreaming or just screen off 71 minutes later.

####About Job PERSIST

If you want your job can be persist after reboot, you really need use `PUBLIC and STATIC` Action class and same modifier Condition.

***Tips***

Trigger will create two directory in your application's data space, named `job_backup` and `job_persist`. `job_backup` keeps the jobs can be recovered after service restarting, so even though this service has been
killed by some reasons, jobs here can be put into the waiting list also, and of course, only persist-able
jobs can be saved here. `job_persist` keeps the jobs can be recovered after device rebooting, if you wan
let your job do this, use the constructors which have `persistAfterReboot` parameter to construct your job instance.

###Last

If you like this project, `STAR` it!

If your application is using this and already online, please let me know through E-mail.

If you have any question, open an issue.

If you have any good idea to improve this project or fixed some bugs, give me a Pull-request, I'm happy to build this project better with your help.


License
---

```
Copyright 2015 Kevin Liu

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

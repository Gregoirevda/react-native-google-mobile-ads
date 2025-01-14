package io.invertase.googlemobileads;

/*
 * Copyright (c) 2016-present Invertase Limited & Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this library except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.BaseAdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.admanager.AdManagerAdView;
import com.google.android.gms.ads.admanager.AppEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ReactNativeGoogleMobileAdsBannerAdViewManager
  extends SimpleViewManager<LayoutWrapper> {
  private static final String REACT_CLASS = "RNGoogleMobileAdsBannerView";
  private final String EVENT_AD_LOADED = "onAdLoaded";
  private final String EVENT_AD_FAILED_TO_LOAD = "onAdFailedToLoad";
  private final String EVENT_AD_OPENED = "onAdOpened";
  private final String EVENT_AD_CLOSED = "onAdClosed";
  private final String EVENT_SIZE_CHANGE = "onSizeChange";
  private final String EVENT_APP_EVENT = "onAppEvent";
  private final int COMMAND_ID_RECORD_MANUAL_IMPRESSION = 1;

  private AdRequest request;
  private List<AdSize> sizes;
  private String unitId;
  private Boolean manualImpressionsEnabled;
  private boolean propsChanged;
  private boolean isFluid;

  @Nonnull
  @Override
  public String getName() {
    return REACT_CLASS;
  }

  @Nonnull
  @Override
  public LayoutWrapper createViewInstance(@Nonnull ThemedReactContext themedReactContext) {
    return new LayoutWrapper(themedReactContext);
  }

  @Override
  public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
    MapBuilder.Builder<String, Object> builder = MapBuilder.builder();
    builder.put("onNativeEvent", MapBuilder.of("registrationName", "onNativeEvent"));
    return builder.build();
  }

  @Nullable
  @Override
  public Map<String, Integer> getCommandsMap() {
    return MapBuilder.of("recordManualImpression", COMMAND_ID_RECORD_MANUAL_IMPRESSION);
  }

  @Override
  public void receiveCommand(
    @NonNull LayoutWrapper reactViewGroup, String commandId, @Nullable ReadableArray args) {
    super.receiveCommand(reactViewGroup, commandId, args);
    int commandIdInt = Integer.parseInt(commandId);

    if (commandIdInt == COMMAND_ID_RECORD_MANUAL_IMPRESSION) {
      BaseAdView adView = reactViewGroup.getContentView();
      if (adView instanceof AdManagerAdView) {
        ((AdManagerAdView) adView).recordManualImpression();
      }
    }
  }

  @ReactProp(name = "unitId")
  public void setUnitId(LayoutWrapper reactViewGroup, String value) {
    unitId = value;
    propsChanged = true;
  }

  @ReactProp(name = "request")
  public void setRequest(LayoutWrapper reactViewGroup, ReadableMap value) {
    request = ReactNativeGoogleMobileAdsCommon.buildAdRequest(value);
    propsChanged = true;
  }

  @ReactProp(name = "sizes")
  public void setSizes(LayoutWrapper reactViewGroup, ReadableArray value) {
    List<AdSize> sizeList = new ArrayList<>();
    for (Object size : value.toArrayList()) {
      if (size instanceof String) {
        String sizeString = (String) size;
        sizeList.add(ReactNativeGoogleMobileAdsCommon.getAdSize(sizeString, reactViewGroup));
      }
    }

    if (sizeList.size() > 0 && !sizeList.contains(AdSize.FLUID)) {
      AdSize adSize = sizeList.get(0);
      WritableMap payload = Arguments.createMap();
      payload.putDouble("width", adSize.getWidth());
      payload.putDouble("height", adSize.getHeight());
      sendEvent(reactViewGroup, EVENT_SIZE_CHANGE, payload);
    }

    sizes = sizeList;
    propsChanged = true;
  }

  @ReactProp(name = "manualImpressionsEnabled")
  public void setManualImpressionsEnabled(LayoutWrapper reactViewGroup, boolean value) {
    this.manualImpressionsEnabled = value;
    propsChanged = true;
  }

  @Override
  public void onAfterUpdateTransaction(@NonNull LayoutWrapper reactViewGroup) {
    super.onAfterUpdateTransaction(reactViewGroup);
    if (propsChanged) {
      requestAd(reactViewGroup);
    }
    propsChanged = false;
  }

  private BaseAdView initAdView(LayoutWrapper reactViewGroup) {
    BaseAdView oldAdView = reactViewGroup.getContentView();
    if (oldAdView != null) {
      oldAdView.destroy();
      reactViewGroup.removeView(oldAdView);
    }
    BaseAdView adView;
    if (ReactNativeGoogleMobileAdsCommon.isAdManagerUnit(unitId)) {
      adView = new AdManagerAdView(reactViewGroup.getContext());
    } else {
      adView = new AdView(reactViewGroup.getContext());
    }
    adView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    adView.setAdListener(
      new AdListener() {
        @Override
        public void onAdLoaded() {
          reactViewGroup.requestLayout();

          if(isFluid) return;

          AdSize adSize = adView.getAdSize();
          WritableMap payload = Arguments.createMap();
          payload.putDouble("width", adSize.getWidth());
          payload.putDouble("height", adSize.getHeight());

          sendEvent(reactViewGroup, EVENT_AD_LOADED, payload);
        }

        @Override
        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
          int errorCode = loadAdError.getCode();
          WritableMap payload = ReactNativeGoogleMobileAdsCommon.errorCodeToMap(errorCode);
          sendEvent(reactViewGroup, EVENT_AD_FAILED_TO_LOAD, payload);
        }

        @Override
        public void onAdOpened() {
          sendEvent(reactViewGroup, EVENT_AD_OPENED, null);
        }

        @Override
        public void onAdClosed() {
          sendEvent(reactViewGroup, EVENT_AD_CLOSED, null);
        }
      });
    if (adView instanceof AdManagerAdView) {
      ((AdManagerAdView) adView)
        .setAppEventListener(
          new AppEventListener() {
            @Override
            public void onAppEvent(@NonNull String name, @Nullable String data) {
              WritableMap payload = Arguments.createMap();
              payload.putString("name", name);
              payload.putString("data", data);
              sendEvent(reactViewGroup, EVENT_APP_EVENT, payload);
            }
          });
    }
    reactViewGroup.addView(adView);
    return adView;
  }

  private void requestAd(LayoutWrapper reactViewGroup) {
    if (sizes == null || unitId == null || request == null || manualImpressionsEnabled == null) {
      return;
    }

    isFluid = sizes.contains(AdSize.FLUID);

    BaseAdView adView = initAdView(reactViewGroup);
    adView.setAdUnitId(unitId);

    if (adView instanceof AdManagerAdView) {
      if (isFluid) {
        ((AdManagerAdView) adView).setAdSize(AdSize.FLUID);
      } else {
        ((AdManagerAdView) adView).setAdSizes(sizes.toArray(new AdSize[0]));
      }
      if (manualImpressionsEnabled) {
        ((AdManagerAdView) adView).setManualImpressionsEnabled(true);
      }
    } else {
      adView.setAdSize(sizes.get(0));
    }

    adView.loadAd(request);
  }

  private void sendEvent(LayoutWrapper reactViewGroup, String type, WritableMap payload) {
    WritableMap event = Arguments.createMap();
    event.putString("type", type);

    if (payload != null) {
      event.merge(payload);
    }

    ((ThemedReactContext) reactViewGroup.getContext())
      .getJSModule(RCTEventEmitter.class)
      .receiveEvent(reactViewGroup.getId(), "onNativeEvent", event);
  }
}

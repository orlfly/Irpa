package com.kingsware.irpa.automation;

import java.util.List;

public interface AppController {
    void onUiElementsFetched(List<AutoAccessibilityService.UiElement> elements);
}
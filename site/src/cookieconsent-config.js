import "vanilla-cookieconsent/dist/cookieconsent.css";
import * as CookieConsent from "vanilla-cookieconsent";

const GA_MEASUREMENT_ID = "G-N4J5QGY48M";

let analyticsLoaded = false;

export function initCookieConsent() {
  CookieConsent.run({
    hideFromBots: false,
    guiOptions: {
      consentModal: {
        layout: "box",
        position: "bottom left",
        equalWeightButtons: true,
        flipButtons: false,
      },
      preferencesModal: {
        layout: "box",
        position: "right",
        equalWeightButtons: true,
        flipButtons: false,
      },
    },
    categories: {
      necessary: { enabled: true, readOnly: true },
      analytics: {
        enabled: false,
        autoClear: {
          cookies: [{ name: /^_ga/ }, { name: "_gid" }],
        },
      },
    },
    language: {
      default: "en",
      translations: {
        en: {
          consentModal: {
            title: "We use cookies",
            description: "This website uses cookies to analyze traffic and improve your experience.",
            acceptAllBtn: "Accept all",
            acceptNecessaryBtn: "Reject all",
            showPreferencesBtn: "Manage preferences",
          },
          preferencesModal: {
            title: "Manage cookie preferences",
            acceptAllBtn: "Accept all",
            acceptNecessaryBtn: "Reject all",
            savePreferencesBtn: "Save preferences",
            closeIconLabel: "Close modal",
            sections: [
              {
                title: "Strictly Necessary cookies",
                description: "Essential for the website to function.",
                linkedCategory: "necessary",
              },
              {
                title: "Analytics Cookies",
                description: "These cookies help us understand how you use our website.",
                linkedCategory: "analytics",
              },
            ],
          },
        },
      },
    },
    onFirstConsent: ({ cookie }) => {
      updateGoogleTagConsent(cookie);
    },
    onConsent: ({ cookie }) => {
      updateGoogleTagConsent(cookie);
    },
    onChange: ({ cookie }) => {
      updateGoogleTagConsent(cookie);
    },
  });
}

function updateGoogleTagConsent(cookie) {
  if (cookie.categories.includes("analytics")) {
    loadAnalytics();
    return;
  }

  if (typeof window.gtag === "function") {
    window.gtag("consent", "update", {
      ad_storage: "denied",
      analytics_storage: "denied",
    });
  }
}

function loadAnalytics() {
  if (analyticsLoaded) {
    window.gtag("consent", "update", {
      ad_storage: "denied",
      analytics_storage: "granted",
    });
    return;
  }

  analyticsLoaded = true;
  window.dataLayer = window.dataLayer || [];
  window.gtag = function gtag() {
    window.dataLayer.push(arguments);
  };

  window.gtag("consent", "default", {
    ad_storage: "denied",
    analytics_storage: "granted",
  });
  window.gtag("js", new Date());
  window.gtag("config", GA_MEASUREMENT_ID);

  const script = document.createElement("script");
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_MEASUREMENT_ID}`;
  document.head.append(script);
}

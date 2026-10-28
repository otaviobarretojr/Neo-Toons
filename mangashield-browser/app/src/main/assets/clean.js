(function() {
  if (document.getElementById('mangashield-cosmetic')) return;
  var style = document.createElement('style');
  style.id = 'mangashield-cosmetic';
  var selectors = [
    'ins.adsbygoogle',
    '.adsbygoogle',
    '.ad-banner',
    '.ads-banner',
    '.ad-container',
    '.advertisement',
    '.advertisement-container',
    '.advertising',
    '[id^="google_ads"]',
    '[id^="div-gpt-ad"]',
    '[data-ad-slot]',
    '[data-ad-client]',
    'iframe[src*="doubleclick.net"]',
    'iframe[src*="googlesyndication.com"]',
    'iframe[src*="adnxs.com"]',
    '[class*="popunder"]',
    '[id*="popunder"]',
    '.popup-ad',
    '.sticky-ad',
    '.floating-ad'
  ];
  style.textContent = selectors.join(',') + '{display:none!important;visibility:hidden!important;max-height:0!important;pointer-events:none!important;}';
  (document.head || document.documentElement).appendChild(style);
  document.documentElement.style.overflowX='hidden';
})();

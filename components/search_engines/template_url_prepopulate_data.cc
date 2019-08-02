// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "components/search_engines/template_url_prepopulate_data.h"

#include "base/logging.h"
#include "base/macros.h"
#include "build/build_config.h"
#include "components/country_codes/country_codes.h"
#include "components/google/core/common/google_util.h"
#include "components/pref_registry/pref_registry_syncable.h"
#include "components/prefs/pref_service.h"
#include "components/search_engines/prepopulated_engines.h"
#include "components/search_engines/search_engines_pref_names.h"
#include "components/search_engines/template_url_data.h"
#include "components/search_engines/template_url_data_util.h"
#include "net/base/registry_controlled_domains/registry_controlled_domain.h"
#include "url/gurl.h"

namespace TemplateURLPrepopulateData {

// Helpers --------------------------------------------------------------------

namespace {

// NOTE: You should probably not change the data in this file without changing
// |kCurrentDataVersion| in prepopulated_engines.json. See comments in
// GetDataVersion() below!

// Put the engines within each country in order with most interesting/important
// first.  The default will be the first engine.

// Default (for countries with no better engine set)
const PrepopulatedEngine* engines_default[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// United Arab Emirates
const PrepopulatedEngine* engines_AE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Albania
const PrepopulatedEngine* engines_AL[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Argentina
const PrepopulatedEngine* engines_AR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Austria
const PrepopulatedEngine* engines_AT[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Australia
const PrepopulatedEngine* engines_AU[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Bosnia and Herzegovina
const PrepopulatedEngine* engines_BA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Belgium
const PrepopulatedEngine* engines_BE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Bulgaria
const PrepopulatedEngine* engines_BG[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Bahrain
const PrepopulatedEngine* engines_BH[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Burundi
const PrepopulatedEngine* engines_BI[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Brunei
const PrepopulatedEngine* engines_BN[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Bolivia
const PrepopulatedEngine* engines_BO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Brazil
const PrepopulatedEngine* engines_BR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Belarus
const PrepopulatedEngine* engines_BY[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_by, };

// Belize
const PrepopulatedEngine* engines_BZ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Canada
const PrepopulatedEngine* engines_CA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Switzerland
const PrepopulatedEngine* engines_CH[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Chile
const PrepopulatedEngine* engines_CL[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// China
const PrepopulatedEngine* engines_CN[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Colombia
const PrepopulatedEngine* engines_CO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Costa Rica
const PrepopulatedEngine* engines_CR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Czech Republic
const PrepopulatedEngine* engines_CZ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Germany
const PrepopulatedEngine* engines_DE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Denmark
const PrepopulatedEngine* engines_DK[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Dominican Republic
const PrepopulatedEngine* engines_DO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Algeria
const PrepopulatedEngine* engines_DZ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Ecuador
const PrepopulatedEngine* engines_EC[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Estonia
const PrepopulatedEngine* engines_EE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Egypt
const PrepopulatedEngine* engines_EG[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Spain
const PrepopulatedEngine* engines_ES[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Faroe Islands
const PrepopulatedEngine* engines_FO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Finland
const PrepopulatedEngine* engines_FI[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// France
const PrepopulatedEngine* engines_FR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// United Kingdom
const PrepopulatedEngine* engines_GB[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Greece
const PrepopulatedEngine* engines_GR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Guatemala
const PrepopulatedEngine* engines_GT[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Hong Kong
const PrepopulatedEngine* engines_HK[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Honduras
const PrepopulatedEngine* engines_HN[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Croatia
const PrepopulatedEngine* engines_HR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Hungary
const PrepopulatedEngine* engines_HU[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Indonesia
const PrepopulatedEngine* engines_ID[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Ireland
const PrepopulatedEngine* engines_IE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Israel
const PrepopulatedEngine* engines_IL[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// India
const PrepopulatedEngine* engines_IN[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Iraq
const PrepopulatedEngine* engines_IQ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Iran
const PrepopulatedEngine* engines_IR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Iceland
const PrepopulatedEngine* engines_IS[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Italy
const PrepopulatedEngine* engines_IT[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Jamaica
const PrepopulatedEngine* engines_JM[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Jordan
const PrepopulatedEngine* engines_JO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Japan
const PrepopulatedEngine* engines_JP[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Kenya
const PrepopulatedEngine* engines_KE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Kuwait
const PrepopulatedEngine* engines_KW[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// South Korea
const PrepopulatedEngine* engines_KR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Kazakhstan
const PrepopulatedEngine* engines_KZ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_kz, };

// Lebanon
const PrepopulatedEngine* engines_LB[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Liechtenstein
const PrepopulatedEngine* engines_LI[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Lithuania
const PrepopulatedEngine* engines_LT[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_ru, };

// Luxembourg
const PrepopulatedEngine* engines_LU[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Latvia
const PrepopulatedEngine* engines_LV[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_ru, };

// Libya
const PrepopulatedEngine* engines_LY[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Morocco
const PrepopulatedEngine* engines_MA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Monaco
const PrepopulatedEngine* engines_MC[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Moldova
const PrepopulatedEngine* engines_MD[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Montenegro
const PrepopulatedEngine* engines_ME[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Macedonia
const PrepopulatedEngine* engines_MK[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Mexico
const PrepopulatedEngine* engines_MX[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Malaysia
const PrepopulatedEngine* engines_MY[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Nicaragua
const PrepopulatedEngine* engines_NI[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Netherlands
const PrepopulatedEngine* engines_NL[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Norway
const PrepopulatedEngine* engines_NO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// New Zealand
const PrepopulatedEngine* engines_NZ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Oman
const PrepopulatedEngine* engines_OM[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Panama
const PrepopulatedEngine* engines_PA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Peru
const PrepopulatedEngine* engines_PE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Philippines
const PrepopulatedEngine* engines_PH[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Pakistan
const PrepopulatedEngine* engines_PK[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Puerto Rico
const PrepopulatedEngine* engines_PR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Poland
const PrepopulatedEngine* engines_PL[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Portugal
const PrepopulatedEngine* engines_PT[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Paraguay
const PrepopulatedEngine* engines_PY[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Qatar
const PrepopulatedEngine* engines_QA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Romania
const PrepopulatedEngine* engines_RO[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Serbia
const PrepopulatedEngine* engines_RS[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Russia
const PrepopulatedEngine* engines_RU[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_ru, };

// Rwanda
const PrepopulatedEngine* engines_RW[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Saudi Arabia
const PrepopulatedEngine* engines_SA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Sweden
const PrepopulatedEngine* engines_SE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Singapore
const PrepopulatedEngine* engines_SG[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Slovenia
const PrepopulatedEngine* engines_SI[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Slovakia
const PrepopulatedEngine* engines_SK[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// El Salvador
const PrepopulatedEngine* engines_SV[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Syria
const PrepopulatedEngine* engines_SY[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Thailand
const PrepopulatedEngine* engines_TH[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Tunisia
const PrepopulatedEngine* engines_TN[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Turkey
const PrepopulatedEngine* engines_TR[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_tr, &yandex, };

// Trinidad and Tobago
const PrepopulatedEngine* engines_TT[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Taiwan
const PrepopulatedEngine* engines_TW[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Tanzania
const PrepopulatedEngine* engines_TZ[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Ukraine
const PrepopulatedEngine* engines_UA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex_ua, };

// United States
const PrepopulatedEngine* engines_US[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Uruguay
const PrepopulatedEngine* engines_UY[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Venezuela
const PrepopulatedEngine* engines_VE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Vietnam
const PrepopulatedEngine* engines_VN[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Yemen
const PrepopulatedEngine* engines_YE[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// South Africa
const PrepopulatedEngine* engines_ZA[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// Zimbabwe
const PrepopulatedEngine* engines_ZW[] =
    { &google, &bing, &duckduckgo, &duckduckgo_light, &qwant, &startpage, &yandex, };

// A list of all the engines that we know about.
const PrepopulatedEngine* kAllEngines[] = {
  // Prepopulated engines:
  &bing,         &duckduckgo,   &google,       &qwant,        &startpage,
  &yandex,       &yandex_by,    &yandex_kz,    &yandex_ru,    &yandex_tr,
  &yandex_ua,    &duckduckgo_light,
};

std::vector<std::unique_ptr<TemplateURLData>> GetPrepopulationSetFromCountryID(
    int country_id) {
  const PrepopulatedEngine* const* engines;
  size_t num_engines;
  // If you add a new country make sure to update the unit test for coverage.
  switch (country_id) {
#define UNHANDLED_COUNTRY(code1, code2) \
  case country_codes::CountryCharsToCountryID((#code1)[0], (#code2)[0]):
#define END_UNHANDLED_COUNTRIES(code1, code2)\
      engines = engines_##code1##code2;\
      num_engines = arraysize(engines_##code1##code2);\
      break;
#define DECLARE_COUNTRY(code1, code2)\
    UNHANDLED_COUNTRY(code1, code2)\
    END_UNHANDLED_COUNTRIES(code1, code2)

    // Countries with their own, dedicated engine set.
    DECLARE_COUNTRY(A, E)  // United Arab Emirates
    DECLARE_COUNTRY(A, L)  // Albania
    DECLARE_COUNTRY(A, R)  // Argentina
    DECLARE_COUNTRY(A, T)  // Austria
    DECLARE_COUNTRY(A, U)  // Australia
    DECLARE_COUNTRY(B, A)  // Bosnia and Herzegovina
    DECLARE_COUNTRY(B, E)  // Belgium
    DECLARE_COUNTRY(B, G)  // Bulgaria
    DECLARE_COUNTRY(B, H)  // Bahrain
    DECLARE_COUNTRY(B, I)  // Burundi
    DECLARE_COUNTRY(B, N)  // Brunei
    DECLARE_COUNTRY(B, O)  // Bolivia
    DECLARE_COUNTRY(B, R)  // Brazil
    DECLARE_COUNTRY(B, Y)  // Belarus
    DECLARE_COUNTRY(B, Z)  // Belize
    DECLARE_COUNTRY(C, A)  // Canada
    DECLARE_COUNTRY(C, H)  // Switzerland
    DECLARE_COUNTRY(C, L)  // Chile
    DECLARE_COUNTRY(C, N)  // China
    DECLARE_COUNTRY(C, O)  // Colombia
    DECLARE_COUNTRY(C, R)  // Costa Rica
    DECLARE_COUNTRY(C, Z)  // Czech Republic
    DECLARE_COUNTRY(D, E)  // Germany
    DECLARE_COUNTRY(D, K)  // Denmark
    DECLARE_COUNTRY(D, O)  // Dominican Republic
    DECLARE_COUNTRY(D, Z)  // Algeria
    DECLARE_COUNTRY(E, C)  // Ecuador
    DECLARE_COUNTRY(E, E)  // Estonia
    DECLARE_COUNTRY(E, G)  // Egypt
    DECLARE_COUNTRY(E, S)  // Spain
    DECLARE_COUNTRY(F, I)  // Finland
    DECLARE_COUNTRY(F, O)  // Faroe Islands
    DECLARE_COUNTRY(F, R)  // France
    DECLARE_COUNTRY(G, B)  // United Kingdom
    DECLARE_COUNTRY(G, R)  // Greece
    DECLARE_COUNTRY(G, T)  // Guatemala
    DECLARE_COUNTRY(H, K)  // Hong Kong
    DECLARE_COUNTRY(H, N)  // Honduras
    DECLARE_COUNTRY(H, R)  // Croatia
    DECLARE_COUNTRY(H, U)  // Hungary
    DECLARE_COUNTRY(I, D)  // Indonesia
    DECLARE_COUNTRY(I, E)  // Ireland
    DECLARE_COUNTRY(I, L)  // Israel
    DECLARE_COUNTRY(I, N)  // India
    DECLARE_COUNTRY(I, Q)  // Iraq
    DECLARE_COUNTRY(I, R)  // Iran
    DECLARE_COUNTRY(I, S)  // Iceland
    DECLARE_COUNTRY(I, T)  // Italy
    DECLARE_COUNTRY(J, M)  // Jamaica
    DECLARE_COUNTRY(J, O)  // Jordan
    DECLARE_COUNTRY(J, P)  // Japan
    DECLARE_COUNTRY(K, E)  // Kenya
    DECLARE_COUNTRY(K, R)  // South Korea
    DECLARE_COUNTRY(K, W)  // Kuwait
    DECLARE_COUNTRY(K, Z)  // Kazakhstan
    DECLARE_COUNTRY(L, B)  // Lebanon
    DECLARE_COUNTRY(L, I)  // Liechtenstein
    DECLARE_COUNTRY(L, T)  // Lithuania
    DECLARE_COUNTRY(L, U)  // Luxembourg
    DECLARE_COUNTRY(L, V)  // Latvia
    DECLARE_COUNTRY(L, Y)  // Libya
    DECLARE_COUNTRY(M, A)  // Morocco
    DECLARE_COUNTRY(M, C)  // Monaco
    DECLARE_COUNTRY(M, D)  // Moldova
    DECLARE_COUNTRY(M, E)  // Montenegro
    DECLARE_COUNTRY(M, K)  // Macedonia
    DECLARE_COUNTRY(M, X)  // Mexico
    DECLARE_COUNTRY(M, Y)  // Malaysia
    DECLARE_COUNTRY(N, I)  // Nicaragua
    DECLARE_COUNTRY(N, L)  // Netherlands
    DECLARE_COUNTRY(N, O)  // Norway
    DECLARE_COUNTRY(N, Z)  // New Zealand
    DECLARE_COUNTRY(O, M)  // Oman
    DECLARE_COUNTRY(P, A)  // Panama
    DECLARE_COUNTRY(P, E)  // Peru
    DECLARE_COUNTRY(P, H)  // Philippines
    DECLARE_COUNTRY(P, K)  // Pakistan
    DECLARE_COUNTRY(P, L)  // Poland
    DECLARE_COUNTRY(P, R)  // Puerto Rico
    DECLARE_COUNTRY(P, T)  // Portugal
    DECLARE_COUNTRY(P, Y)  // Paraguay
    DECLARE_COUNTRY(Q, A)  // Qatar
    DECLARE_COUNTRY(R, O)  // Romania
    DECLARE_COUNTRY(R, S)  // Serbia
    DECLARE_COUNTRY(R, U)  // Russia
    DECLARE_COUNTRY(R, W)  // Rwanda
    DECLARE_COUNTRY(S, A)  // Saudi Arabia
    DECLARE_COUNTRY(S, E)  // Sweden
    DECLARE_COUNTRY(S, G)  // Singapore
    DECLARE_COUNTRY(S, I)  // Slovenia
    DECLARE_COUNTRY(S, K)  // Slovakia
    DECLARE_COUNTRY(S, V)  // El Salvador
    DECLARE_COUNTRY(S, Y)  // Syria
    DECLARE_COUNTRY(T, H)  // Thailand
    DECLARE_COUNTRY(T, N)  // Tunisia
    DECLARE_COUNTRY(T, R)  // Turkey
    DECLARE_COUNTRY(T, T)  // Trinidad and Tobago
    DECLARE_COUNTRY(T, W)  // Taiwan
    DECLARE_COUNTRY(T, Z)  // Tanzania
    DECLARE_COUNTRY(U, A)  // Ukraine
    DECLARE_COUNTRY(U, S)  // United States
    DECLARE_COUNTRY(U, Y)  // Uruguay
    DECLARE_COUNTRY(V, E)  // Venezuela
    DECLARE_COUNTRY(V, N)  // Vietnam
    DECLARE_COUNTRY(Y, E)  // Yemen
    DECLARE_COUNTRY(Z, A)  // South Africa
    DECLARE_COUNTRY(Z, W)  // Zimbabwe

    // Countries using the "Australia" engine set.
    UNHANDLED_COUNTRY(C, C)  // Cocos Islands
    UNHANDLED_COUNTRY(C, X)  // Christmas Island
    UNHANDLED_COUNTRY(H, M)  // Heard Island and McDonald Islands
    UNHANDLED_COUNTRY(N, F)  // Norfolk Island
    END_UNHANDLED_COUNTRIES(A, U)

    // Countries using the "China" engine set.
    UNHANDLED_COUNTRY(M, O)  // Macao
    END_UNHANDLED_COUNTRIES(C, N)

    // Countries using the "Denmark" engine set.
    UNHANDLED_COUNTRY(G, L)  // Greenland
    END_UNHANDLED_COUNTRIES(D, K)

    // Countries using the "Spain" engine set.
    UNHANDLED_COUNTRY(A, D)  // Andorra
    END_UNHANDLED_COUNTRIES(E, S)

    // Countries using the "Finland" engine set.
    UNHANDLED_COUNTRY(A, X)  // Aland Islands
    END_UNHANDLED_COUNTRIES(F, I)

    // Countries using the "France" engine set.
    UNHANDLED_COUNTRY(B, F)  // Burkina Faso
    UNHANDLED_COUNTRY(B, J)  // Benin
    UNHANDLED_COUNTRY(C, D)  // Congo - Kinshasa
    UNHANDLED_COUNTRY(C, F)  // Central African Republic
    UNHANDLED_COUNTRY(C, G)  // Congo - Brazzaville
    UNHANDLED_COUNTRY(C, I)  // Ivory Coast
    UNHANDLED_COUNTRY(C, M)  // Cameroon
    UNHANDLED_COUNTRY(D, J)  // Djibouti
    UNHANDLED_COUNTRY(G, A)  // Gabon
    UNHANDLED_COUNTRY(G, F)  // French Guiana
    UNHANDLED_COUNTRY(G, N)  // Guinea
    UNHANDLED_COUNTRY(G, P)  // Guadeloupe
    UNHANDLED_COUNTRY(H, T)  // Haiti
#if defined(OS_WIN)
    UNHANDLED_COUNTRY(I, P)  // Clipperton Island ('IP' is an WinXP-ism; ISO
                             //                    includes it with France)
#endif
    UNHANDLED_COUNTRY(M, L)  // Mali
    UNHANDLED_COUNTRY(M, Q)  // Martinique
    UNHANDLED_COUNTRY(N, C)  // New Caledonia
    UNHANDLED_COUNTRY(N, E)  // Niger
    UNHANDLED_COUNTRY(P, F)  // French Polynesia
    UNHANDLED_COUNTRY(P, M)  // Saint Pierre and Miquelon
    UNHANDLED_COUNTRY(R, E)  // Reunion
    UNHANDLED_COUNTRY(S, N)  // Senegal
    UNHANDLED_COUNTRY(T, D)  // Chad
    UNHANDLED_COUNTRY(T, F)  // French Southern Territories
    UNHANDLED_COUNTRY(T, G)  // Togo
    UNHANDLED_COUNTRY(W, F)  // Wallis and Futuna
    UNHANDLED_COUNTRY(Y, T)  // Mayotte
    END_UNHANDLED_COUNTRIES(F, R)

    // Countries using the "Greece" engine set.
    UNHANDLED_COUNTRY(C, Y)  // Cyprus
    END_UNHANDLED_COUNTRIES(G, R)

    // Countries using the "Italy" engine set.
    UNHANDLED_COUNTRY(S, M)  // San Marino
    UNHANDLED_COUNTRY(V, A)  // Vatican
    END_UNHANDLED_COUNTRIES(I, T)

    // Countries using the "Morocco" engine set.
    UNHANDLED_COUNTRY(E, H)  // Western Sahara
    END_UNHANDLED_COUNTRIES(M, A)

    // Countries using the "Netherlands" engine set.
    UNHANDLED_COUNTRY(A, N)  // Netherlands Antilles
    UNHANDLED_COUNTRY(A, W)  // Aruba
    END_UNHANDLED_COUNTRIES(N, L)

    // Countries using the "Norway" engine set.
    UNHANDLED_COUNTRY(B, V)  // Bouvet Island
    UNHANDLED_COUNTRY(S, J)  // Svalbard and Jan Mayen
    END_UNHANDLED_COUNTRIES(N, O)

    // Countries using the "New Zealand" engine set.
    UNHANDLED_COUNTRY(C, K)  // Cook Islands
    UNHANDLED_COUNTRY(N, U)  // Niue
    UNHANDLED_COUNTRY(T, K)  // Tokelau
    END_UNHANDLED_COUNTRIES(N, Z)

    // Countries using the "Portugal" engine set.
    UNHANDLED_COUNTRY(C, V)  // Cape Verde
    UNHANDLED_COUNTRY(G, W)  // Guinea-Bissau
    UNHANDLED_COUNTRY(M, Z)  // Mozambique
    UNHANDLED_COUNTRY(S, T)  // Sao Tome and Principe
    UNHANDLED_COUNTRY(T, L)  // Timor-Leste
    END_UNHANDLED_COUNTRIES(P, T)

    // Countries using the "Russia" engine set.
    UNHANDLED_COUNTRY(A, M)  // Armenia
    UNHANDLED_COUNTRY(A, Z)  // Azerbaijan
    UNHANDLED_COUNTRY(K, G)  // Kyrgyzstan
    UNHANDLED_COUNTRY(T, J)  // Tajikistan
    UNHANDLED_COUNTRY(T, M)  // Turkmenistan
    UNHANDLED_COUNTRY(U, Z)  // Uzbekistan
    END_UNHANDLED_COUNTRIES(R, U)

    // Countries using the "Saudi Arabia" engine set.
    UNHANDLED_COUNTRY(M, R)  // Mauritania
    UNHANDLED_COUNTRY(P, S)  // Palestinian Territory
    UNHANDLED_COUNTRY(S, D)  // Sudan
    END_UNHANDLED_COUNTRIES(S, A)

    // Countries using the "United Kingdom" engine set.
    UNHANDLED_COUNTRY(B, M)  // Bermuda
    UNHANDLED_COUNTRY(F, K)  // Falkland Islands
    UNHANDLED_COUNTRY(G, G)  // Guernsey
    UNHANDLED_COUNTRY(G, I)  // Gibraltar
    UNHANDLED_COUNTRY(G, S)  // South Georgia and the South Sandwich
                             //   Islands
    UNHANDLED_COUNTRY(I, M)  // Isle of Man
    UNHANDLED_COUNTRY(I, O)  // British Indian Ocean Territory
    UNHANDLED_COUNTRY(J, E)  // Jersey
    UNHANDLED_COUNTRY(K, Y)  // Cayman Islands
    UNHANDLED_COUNTRY(M, S)  // Montserrat
    UNHANDLED_COUNTRY(M, T)  // Malta
    UNHANDLED_COUNTRY(P, N)  // Pitcairn Islands
    UNHANDLED_COUNTRY(S, H)  // Saint Helena, Ascension Island, and Tristan da
                             //   Cunha
    UNHANDLED_COUNTRY(T, C)  // Turks and Caicos Islands
    UNHANDLED_COUNTRY(V, G)  // British Virgin Islands
    END_UNHANDLED_COUNTRIES(G, B)

    // Countries using the "United States" engine set.
    UNHANDLED_COUNTRY(A, S)  // American Samoa
    UNHANDLED_COUNTRY(G, U)  // Guam
    UNHANDLED_COUNTRY(M, P)  // Northern Mariana Islands
    UNHANDLED_COUNTRY(U, M)  // U.S. Minor Outlying Islands
    UNHANDLED_COUNTRY(V, I)  // U.S. Virgin Islands
    END_UNHANDLED_COUNTRIES(U, S)

    // Countries using the "default" engine set.
    UNHANDLED_COUNTRY(A, F)  // Afghanistan
    UNHANDLED_COUNTRY(A, G)  // Antigua and Barbuda
    UNHANDLED_COUNTRY(A, I)  // Anguilla
    UNHANDLED_COUNTRY(A, O)  // Angola
    UNHANDLED_COUNTRY(A, Q)  // Antarctica
    UNHANDLED_COUNTRY(B, B)  // Barbados
    UNHANDLED_COUNTRY(B, D)  // Bangladesh
    UNHANDLED_COUNTRY(B, S)  // Bahamas
    UNHANDLED_COUNTRY(B, T)  // Bhutan
    UNHANDLED_COUNTRY(B, W)  // Botswana
    UNHANDLED_COUNTRY(C, U)  // Cuba
    UNHANDLED_COUNTRY(D, M)  // Dominica
    UNHANDLED_COUNTRY(E, R)  // Eritrea
    UNHANDLED_COUNTRY(E, T)  // Ethiopia
    UNHANDLED_COUNTRY(F, J)  // Fiji
    UNHANDLED_COUNTRY(F, M)  // Micronesia
    UNHANDLED_COUNTRY(G, D)  // Grenada
    UNHANDLED_COUNTRY(G, E)  // Georgia
    UNHANDLED_COUNTRY(G, H)  // Ghana
    UNHANDLED_COUNTRY(G, M)  // Gambia
    UNHANDLED_COUNTRY(G, Q)  // Equatorial Guinea
    UNHANDLED_COUNTRY(G, Y)  // Guyana
    UNHANDLED_COUNTRY(K, H)  // Cambodia
    UNHANDLED_COUNTRY(K, I)  // Kiribati
    UNHANDLED_COUNTRY(K, M)  // Comoros
    UNHANDLED_COUNTRY(K, N)  // Saint Kitts and Nevis
    UNHANDLED_COUNTRY(K, P)  // North Korea
    UNHANDLED_COUNTRY(L, A)  // Laos
    UNHANDLED_COUNTRY(L, C)  // Saint Lucia
    UNHANDLED_COUNTRY(L, K)  // Sri Lanka
    UNHANDLED_COUNTRY(L, R)  // Liberia
    UNHANDLED_COUNTRY(L, S)  // Lesotho
    UNHANDLED_COUNTRY(M, G)  // Madagascar
    UNHANDLED_COUNTRY(M, H)  // Marshall Islands
    UNHANDLED_COUNTRY(M, M)  // Myanmar
    UNHANDLED_COUNTRY(M, N)  // Mongolia
    UNHANDLED_COUNTRY(M, U)  // Mauritius
    UNHANDLED_COUNTRY(M, V)  // Maldives
    UNHANDLED_COUNTRY(M, W)  // Malawi
    UNHANDLED_COUNTRY(N, A)  // Namibia
    UNHANDLED_COUNTRY(N, G)  // Nigeria
    UNHANDLED_COUNTRY(N, P)  // Nepal
    UNHANDLED_COUNTRY(N, R)  // Nauru
    UNHANDLED_COUNTRY(P, G)  // Papua New Guinea
    UNHANDLED_COUNTRY(P, W)  // Palau
    UNHANDLED_COUNTRY(S, B)  // Solomon Islands
    UNHANDLED_COUNTRY(S, C)  // Seychelles
    UNHANDLED_COUNTRY(S, L)  // Sierra Leone
    UNHANDLED_COUNTRY(S, O)  // Somalia
    UNHANDLED_COUNTRY(S, R)  // Suriname
    UNHANDLED_COUNTRY(S, Z)  // Swaziland
    UNHANDLED_COUNTRY(T, O)  // Tonga
    UNHANDLED_COUNTRY(T, V)  // Tuvalu
    UNHANDLED_COUNTRY(U, G)  // Uganda
    UNHANDLED_COUNTRY(V, C)  // Saint Vincent and the Grenadines
    UNHANDLED_COUNTRY(V, U)  // Vanuatu
    UNHANDLED_COUNTRY(W, S)  // Samoa
    UNHANDLED_COUNTRY(Z, M)  // Zambia
    case country_codes::kCountryIDUnknown:
    default:                // Unhandled location
    END_UNHANDLED_COUNTRIES(def, ault)
  }

  std::vector<std::unique_ptr<TemplateURLData>> t_urls;
  for (size_t i = 0; i < num_engines; ++i)
    t_urls.push_back(TemplateURLDataFromPrepopulatedEngine(*engines[i]));
  return t_urls;
}

std::vector<std::unique_ptr<TemplateURLData>> GetPrepopulatedTemplateURLData(
    PrefService* prefs) {
  std::vector<std::unique_ptr<TemplateURLData>> t_urls;
  if (!prefs)
    return t_urls;

  const base::ListValue* list = prefs->GetList(prefs::kSearchProviderOverrides);
  if (!list)
    return t_urls;

  size_t num_engines = list->GetSize();
  for (size_t i = 0; i != num_engines; ++i) {
    const base::DictionaryValue* engine;
    if (list->GetDictionary(i, &engine)) {
      auto t_url = TemplateURLDataFromOverrideDictionary(*engine);
      if (t_url)
        t_urls.push_back(std::move(t_url));
    }
  }
  return t_urls;
}

bool SameDomain(const GURL& given_url, const GURL& prepopulated_url) {
  return prepopulated_url.is_valid() &&
      net::registry_controlled_domains::SameDomainOrHost(
          given_url, prepopulated_url,
          net::registry_controlled_domains::INCLUDE_PRIVATE_REGISTRIES);
}

}  // namespace

// Global functions -----------------------------------------------------------

void RegisterProfilePrefs(user_prefs::PrefRegistrySyncable* registry) {
  country_codes::RegisterProfilePrefs(registry);
  registry->RegisterListPref(prefs::kSearchProviderOverrides);
  registry->RegisterIntegerPref(prefs::kSearchProviderOverridesVersion, -1);
}

int GetDataVersion(PrefService* prefs) {
  // Allow tests to override the local version.
  return (prefs && prefs->HasPrefPath(prefs::kSearchProviderOverridesVersion)) ?
      prefs->GetInteger(prefs::kSearchProviderOverridesVersion) :
      kCurrentDataVersion;
}

std::vector<std::unique_ptr<TemplateURLData>> GetPrepopulatedEngines(
    PrefService* prefs,
    size_t* default_search_provider_index) {
  // If there is a set of search engines in the preferences file, it overrides
  // the built-in set.
  if (default_search_provider_index)
    *default_search_provider_index = 0;
  std::vector<std::unique_ptr<TemplateURLData>> t_urls =
      GetPrepopulatedTemplateURLData(prefs);
  if (!t_urls.empty())
    return t_urls;

  return GetPrepopulationSetFromCountryID(
      country_codes::GetCountryIDFromPrefs(prefs));
}

std::unique_ptr<TemplateURLData> GetPrepopulatedEngine(PrefService* prefs,
                                                       int prepopulated_id) {
  size_t default_index;
  auto engines =
      TemplateURLPrepopulateData::GetPrepopulatedEngines(prefs, &default_index);
  for (auto& engine : engines) {
    if (engine->prepopulate_id == prepopulated_id)
      return std::move(engine);
  }
  return nullptr;
}

#if defined(OS_ANDROID)

std::vector<std::unique_ptr<TemplateURLData>> GetLocalPrepopulatedEngines(
    const std::string& locale) {
  int country_id = country_codes::CountryStringToCountryID(locale);
  if (country_id == country_codes::kCountryIDUnknown) {
    LOG(ERROR) << "Unknown country code specified: " << locale;
    return std::vector<std::unique_ptr<TemplateURLData>>();
  }

  return GetPrepopulationSetFromCountryID(country_id);
}

#endif

std::vector<const PrepopulatedEngine*> GetAllPrepopulatedEngines() {
  return std::vector<const PrepopulatedEngine*>(std::begin(kAllEngines),
                                                std::end(kAllEngines));
}

void ClearPrepopulatedEnginesInPrefs(PrefService* prefs) {
  if (!prefs)
    return;

  prefs->ClearPref(prefs::kSearchProviderOverrides);
  prefs->ClearPref(prefs::kSearchProviderOverridesVersion);
}

std::unique_ptr<TemplateURLData> GetPrepopulatedDefaultSearch(
    PrefService* prefs) {
  size_t default_search_index;
  // This could be more efficient.  We are loading all the URLs to only keep
  // the first one.
  std::vector<std::unique_ptr<TemplateURLData>> loaded_urls =
      GetPrepopulatedEngines(prefs, &default_search_index);

  return (default_search_index < loaded_urls.size())
             ? std::move(loaded_urls[default_search_index])
             : nullptr;
}

SearchEngineType GetEngineType(const GURL& url) {
  DCHECK(url.is_valid());

  // Check using TLD+1s, in order to more aggressively match search engine types
  // for data imported from other browsers.
  //
  // First special-case Google, because the prepopulate URL for it will not
  // convert to a GURL and thus won't have an origin.  Instead see if the
  // incoming URL's host is "[*.]google.<TLD>".
  if (google_util::IsGoogleHostname(url.host(),
                                    google_util::DISALLOW_SUBDOMAIN))
    return google.type;

  // Now check the rest of the prepopulate data.
  for (size_t i = 0; i < arraysize(kAllEngines); ++i) {
    // First check the main search URL.
    if (SameDomain(url, GURL(kAllEngines[i]->search_url)))
      return kAllEngines[i]->type;

    // Then check the alternate URLs.
    for (size_t j = 0; j < kAllEngines[i]->alternate_urls_size; ++j) {
      if (SameDomain(url, GURL(kAllEngines[i]->alternate_urls[j])))
        return kAllEngines[i]->type;
    }
  }

  return SEARCH_ENGINE_OTHER;
}

}  // namespace TemplateURLPrepopulateData

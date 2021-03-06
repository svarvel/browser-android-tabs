// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.
#include "chrome/browser/media/router/discovery/dial/safe_dial_app_info_parser.h"

#include <string>

#include "base/bind.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/run_loop.h"
#include "base/strings/string_util.h"
#include "chrome/browser/media/router/data_decoder_util.h"
#include "content/public/test/test_browser_thread_bundle.h"
#include "services/data_decoder/data_decoder_service.h"
#include "services/data_decoder/public/cpp/safe_xml_parser.h"
#include "services/service_manager/public/cpp/test/test_connector_factory.h"
#include "testing/gtest/include/gtest/gtest.h"

namespace media_router {

namespace {

constexpr char kValidAppInfoXml[] =
    R"(<?xml version="1.0" encoding="UTF-8"?>
       <service xmlns="urn:dial-multiscreen-org:schemas:dial">
        <name>YouTube</name>
        <options allowStop="false"/>
        <state>running</state>
        <link rel="run" href="run"/>
       </service>
    )";

constexpr char kValidAppInfoXmlExtraData[] =
    R"(<?xml version="1.0" encoding="UTF-8"?>
       <service xmlns="urn:dial-multiscreen-org:schemas:dial">
         <name>YouTube</name>
         <state>Running</state>
         <options allowStop="false"/>
         <link rel="run" href="run"/>
         <port>8080</port>
         <capabilities>websocket</capabilities>
         <additionalData>
           <screenId>e5n3112oskr42pg0td55b38nh4</screenId>
           <otherField>2</otherField>
         </additionalData>
       </service>
    )";

constexpr char kInvalidXmlNoState[] =
    R"(<?xml version="1.0" encoding="UTF-8"?>
       <service xmlns="urn:dial-multiscreen-org:schemas:dial">
         <name>YouTube</name>
         <state></state>
         <options allowStop="false"/>
         <link rel="run" href="run"/>
       </service>
    )";

constexpr char kInvalidXmlInvalidState[] =
    R"(<?xml version="1.0" encoding="UTF-8"?>
       <service xmlns="urn:dial-multiscreen-org:schemas:dial">
         <name>YouTube</name>
         <options allowStop="false"/>
         <state>xyzzy</state>
         <link rel="run" href="run"/>
       </service>
    )";

constexpr char kInvalidXmlNoName[] =
    R"(<?xml version="1.0" encoding="UTF-8"?>
       <service xmlns="urn:dial-multiscreen-org:schemas:dial">
         <options allowStop="false"/>
         <state>running</state>
         <link rel="run" href="run"/>
       </service>
    )";

constexpr char kInvalidXmlMultipleServices[] =
    R"(<?xml version="1.0" encoding="UTF-8"?>
       <root>
         <service xmlns="urn:dial-multiscreen-org:schemas:dial">
           <name>YouTube</name>
           <options allowStop="false"/>
           <state>running</state>
         </service>
         <service xmlns="urn:dial-multiscreen-org:schemas:dial">
           <name>Netflix</name>
           <options allowStop="false"/>
           <state>running</state>
         </service>
       </root>
    )";

}  // namespace

class SafeDialAppInfoParserTest : public testing::Test {
 public:
  SafeDialAppInfoParserTest()
      : connector_factory_(
            service_manager::TestConnectorFactory::CreateForUniqueService(
                std::make_unique<data_decoder::DataDecoderService>())),
        connector_(connector_factory_->CreateConnector()) {}

  std::unique_ptr<ParsedDialAppInfo> Parse(
      const std::string& xml,
      SafeDialAppInfoParser::ParsingResult expected_result) {
    base::RunLoop run_loop;
    DataDecoder data_decoder(connector_.get());
    SafeDialAppInfoParser parser(&data_decoder);
    parser.Parse(xml,
                 base::BindOnce(&SafeDialAppInfoParserTest::OnParsingCompleted,
                                base::Unretained(this), expected_result));
    base::RunLoop().RunUntilIdle();
    return std::move(app_info_);
  }

  void OnParsingCompleted(SafeDialAppInfoParser::ParsingResult expected_result,
                          std::unique_ptr<ParsedDialAppInfo> app_info,
                          SafeDialAppInfoParser::ParsingResult result) {
    app_info_ = std::move(app_info);
    EXPECT_EQ(expected_result, result);
  }

 private:
  content::TestBrowserThreadBundle test_browser_thread_bundle_;
  std::unique_ptr<service_manager::TestConnectorFactory> connector_factory_;
  std::unique_ptr<service_manager::Connector> connector_;
  std::unique_ptr<ParsedDialAppInfo> app_info_;
  DISALLOW_COPY_AND_ASSIGN(SafeDialAppInfoParserTest);
};

TEST_F(SafeDialAppInfoParserTest, TestInvalidXmlNoService) {
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse("", SafeDialAppInfoParser::ParsingResult::kInvalidXML);
  EXPECT_FALSE(app_info);
}

TEST_F(SafeDialAppInfoParserTest, TestValidXml) {
  std::string xml_text(kValidAppInfoXml);
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse(xml_text, SafeDialAppInfoParser::ParsingResult::kSuccess);

  EXPECT_EQ("YouTube", app_info->name);
  EXPECT_EQ(DialAppState::kRunning, app_info->state);
  EXPECT_FALSE(app_info->allow_stop);
  EXPECT_EQ("run", app_info->href);
  EXPECT_TRUE(app_info->extra_data.empty());
}

TEST_F(SafeDialAppInfoParserTest, TestValidXmlExtraData) {
  std::string xml_text(kValidAppInfoXmlExtraData);
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse(xml_text, SafeDialAppInfoParser::ParsingResult::kSuccess);

  EXPECT_EQ("YouTube", app_info->name);
  EXPECT_EQ(DialAppState::kRunning, app_info->state);
  EXPECT_EQ(2u, app_info->extra_data.size());
  EXPECT_EQ("8080", app_info->extra_data["port"]);
  EXPECT_EQ("websocket", app_info->extra_data["capabilities"]);
}

TEST_F(SafeDialAppInfoParserTest, TestInvalidXmlNoState) {
  std::string xml_text(kInvalidXmlNoState);
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse(xml_text, SafeDialAppInfoParser::ParsingResult::kFailToReadState);
  EXPECT_FALSE(app_info);
}

TEST_F(SafeDialAppInfoParserTest, TestInvalidXmlInvalidState) {
  std::string xml_text(kInvalidXmlInvalidState);
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse(xml_text, SafeDialAppInfoParser::ParsingResult::kInvalidState);
  EXPECT_FALSE(app_info);
}

TEST_F(SafeDialAppInfoParserTest, TestInvalidXmlNoName) {
  std::string xml_text(kInvalidXmlNoName);
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse(xml_text, SafeDialAppInfoParser::ParsingResult::kMissingName);
  EXPECT_FALSE(app_info);
}

TEST_F(SafeDialAppInfoParserTest, TestInvalidXmlMultipleServices) {
  std::string xml_text(kInvalidXmlMultipleServices);
  std::unique_ptr<ParsedDialAppInfo> app_info =
      Parse(xml_text, SafeDialAppInfoParser::ParsingResult::kInvalidXML);
  EXPECT_FALSE(app_info);
}

}  // namespace media_router

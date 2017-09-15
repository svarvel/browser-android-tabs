import sys
import os.path
import xml.etree.ElementTree
import FP
from os import walk

# These are hard coded values
chrome_strings_file='../../../../chrome/android/java/strings/android_chrome_strings.grd'
translations_folder='../../../../chrome/android/java/strings/translations'
components_folder='../../../../components/strings'
brave_brand_string='Brave'
chrome_brand_strings={'Chrome', 'Google Chrome', 'Chromium'}
brave_ids={}
# Checked manually that these messages are indeed duplicated
duplicated_messages={'Open in new Brave tab', 'Link opened in Brave'}

# Go through .xtb files and replace ids
def ReplaceIds(folder):
    replacingNumber = 1
    for (dirpath, dirnames, filenames) in walk(folder):
        for filename in filenames:
            if filename.endswith('.xtb') and not filename.startswith('components_locale_settings'):
                translations_tree = xml.etree.ElementTree.parse(folder + '/' + filename)
                translations = translations_tree.getroot()
                print('Processing file "' + filename + '"...')
                found_id = False
                for translation_tag in translations.findall('translation'):
                    translation_id = long(translation_tag.get('id'))
                    if translation_id in brave_ids:
                        print('Replacing "' + str(translation_id) + '" with "' + str(brave_ids[translation_id]) + '"')
                        translation_tag.set('id', str(brave_ids[translation_id]))
                        found_id = True
                if found_id:
                    new_file_name = folder + '/' + filename
                    translations_tree.write(new_file_name, encoding="utf-8", xml_declaration=False)
                    # We need to add prepend headers
                    f = open(new_file_name, 'r+')
                    # Load all content to the memory to make it faster (size is less than 1Mb, so should not be a problem)
                    content = f.read()
                    f.seek(0, 0)
                    f.write(('<?xml version="1.0" ?>\n<!DOCTYPE translationbundle>\n') + content)
                    f.close()
    print('Brave ids successfully updated in ' + folder)

# Check for Brave branded strings in grd file, calculate their ids and update them in xtb files (instead of Chrome, Google Chrome and Chromium)
def UpdateBraveIds():
    messages = xml.etree.ElementTree.parse(chrome_strings_file).getroot().find('release').find('messages')
    for message_tag in messages.findall('message'):
        brave_string = message_tag.text
        if brave_brand_string in brave_string:
            brave_string_phs = message_tag.findall('ph')
            for brave_string_ph in brave_string_phs:
                brave_string = brave_string + brave_string_ph.get('name').upper() + brave_string_ph.tail
            brave_string = brave_string.strip().encode('utf-8')
            # Calculate Brave string id
            brave_string_fp = FP.FingerPrint(brave_string) & 0x7fffffffffffffffL
            print(str(brave_string_fp) + ' - ' + brave_string)
            for chrome_brand_string in chrome_brand_strings:
                chrome_string = brave_string.replace(brave_brand_string, chrome_brand_string)
                # Calculate Chrome string id
                chrome_string_fp = FP.FingerPrint(chrome_string) & 0x7fffffffffffffffL
                print(str(chrome_string_fp) + ' - ' + chrome_string)
                if chrome_string_fp in brave_ids:
                    if brave_string not in duplicated_messages:
                        sys.exit('String "' + chrome_string + '" is duplicated')
                brave_ids[chrome_string_fp] = brave_string_fp
            print('\n')
    ReplaceIds(translations_folder)
    ReplaceIds(components_folder)
                        

# Todo: next time add function to replace Chrome strings with Brave strings
# Make changes to strings in folders (chrome/android/java/strings/*.*, components/strings/*.*):
# Google Chrome -> Brave
# Google Chrom. -> Brave (this is regex)
# Chrome -> Brave (except for Chrome OS)
# Chrom. -> Brave (this is regex)
# Chrom.. -> Brave (this is regex)
# Chromium -> Brave (except for Chromium OS and refer that Brave is based on Chromium)
# Google Inc -> Brave Software Inc
# Copyright <ph name="year">%1$d<ex>2014</ex></ph> Google Inc. All rights reserved. -> Copyright <ph name="YEAR">%1$d<ex>2017</ex></ph> Brave Software Inc. All rights reserved.
# https://www.google.com/intl/[GRITLANGCODE]/chrome/browser/privacy/ -> https://brave.com/privacy_android
# https://www.google.com/intl/[GRITLANGCODE]/chrome/browser/privacy/eula_text.html -> https://brave.com/terms_of_use

UpdateBraveIds()

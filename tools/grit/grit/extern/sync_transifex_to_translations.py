import sys
import os.path
import xml.etree.ElementTree
import FP
from os import walk

# these are hard coded values
transifex_folder='../../../../chrome/android/java/strings/transifex'
translations_folder='../../../../chrome/android/java/strings/translations'
base_strings_file='../../../../chrome/android/java/strings/strings.xml'

# Generate message id from message text and meaning string (optional),
# both in utf-8 encoding
#
def GenerateMessageId(message, meaning=''):
    fp = FP.FingerPrint(message)
    if meaning:
        # combine the fingerprints of message and meaning
        fp2 = FP.FingerPrint(meaning)
        if fp < 0:
            fp = fp2 + (fp << 1) + 1
        else:
            fp = fp2 + (fp << 1)
    # To avoid negative ids we strip the high-order bit
    return fp & 0x7fffffffffffffffL

def GetFullTextFromTag(string_tag, parse_ph):
    string_value = string_tag.text
    string_value_phs = string_tag.findall('ph')
    for string_value_ph in string_value_phs:
        if parse_ph:
            string_value = (string_value if string_value is not None else '') + string_value_ph.get('name').upper() + (string_value_ph.tail if string_value_ph.tail is not None else '')
        else:
            if string_value_ph.text is not None:
                string_value_ph.text = ''
            string_value = (string_value if string_value is not None else '') + xml.etree.ElementTree.tostring(string_value_ph, encoding='utf-8').decode('utf-8')
    return string_value

def CleanUpString(string):
    return string.replace("\\'", "'").replace('\\"', '"')

def UpdateTagText(new_translation_tag, string_tag):
    new_translation_tag.text = CleanUpString(string_tag.text)
    # clean up old place holders
    new_translation_tag_phs = new_translation_tag.findall('ph')
    for new_translation_tag_ph in new_translation_tag_phs:
        new_translation_tag.remove(new_translation_tag_ph)
    # apply new place holders
    string_value_phs = string_tag.findall('ph')
    for string_value_ph in string_value_phs:
        if string_value_ph.tail is not None:
            string_value_ph.tail = CleanUpString(string_value_ph.tail)
        if string_value_ph.text is not None:
            string_value_ph.text = ''
        new_translation_tag.append(string_value_ph)

# sync translations from .xtb files to .xml files to be uploaded on transifex
def SyncTranslationsToTransifex():
    # load all strings and calculate their translation id (ignore memory consumption at this point to speed up whole process)
    brave_strings={}
    e = xml.etree.ElementTree.parse(base_strings_file).getroot()
    for string_tag in e.findall('string'):
        string_name = string_tag.get('name')
        string_value = string_tag.text
        if not string_name:
            sys.exit('String name is empty')
        if not string_value:
            sys.exit("String value is empty")
        # calculate translation id
        string_fp = GenerateMessageId(string_value, None)
        if string_name in brave_strings:
            sys.exit('String name "' + string_name + '" is duplicated')
        brave_strings[string_name] = string_fp

    # go through all .xtb files in translations_folder
    replacingNumber = 1
    for (dirpath, dirnames, filenames) in walk(translations_folder):
        for filename in filenames:
            if filename.endswith('.xtb'):
                translations = xml.etree.ElementTree.parse(translations_folder + '/' + filename).getroot()
                # get language id
                lang_id = translations.get('lang').replace('-', '_')
                if not lang_id:
                    sys.exit('Language id not found for ' + filename)
                # if not lang_id == 'uk':
                #   continue
                #there are some differences in language codes, so correct them
                if lang_id == 'ca':
                    lang_id = 'ca_ES'
                elif lang_id == 'bg':
                    lang_id = 'bg_BG'
                elif lang_id == 'iw':
                    lang_id = 'he'
                elif lang_id == 'cs':
                    lang_id = 'cs_CZ'            
                print('Processing language "' + lang_id + '"...')
                # find appropriate xml file in transifex folder
                xml_file_name = transifex_folder + '/stringsxml_' + lang_id + '.xml'
                if os.path.isfile(xml_file_name):
                    # go through all strings in a file name
                    strings_tree = xml.etree.ElementTree.parse(xml_file_name)
                    strings_file_was_changed = False
                    strings = strings_tree.getroot()
                    for string_tag in strings.findall('string'):
                        string_name = string_tag.get('name')
                        string_value = string_tag.text
                        if string_name in brave_strings:
                            # we have its translation id, lets look for it in .xtb file
                            translation_id_found = False                        
                            for translation_tag in translations.findall('translation'):
                                translation_id = translation_tag.get('id')
                                translation_text = translation_tag.text
                                # we found id, so replace it
                                if translation_id == str(brave_strings[string_name]):
                                    if not translation_text == string_value:
                                        print(str(replacingNumber) + ' replacing "' + string_value + '" with "' + translation_text + '"')
                                        replacingNumber += 1
                                        string_tag.text = translation_text
                                        strings_file_was_changed = True
                                    translation_id_found = True
                                    break
                            # could not find translation id, so append it to the end
                            if not translation_id_found:
                                sys.exit('Translation id "' + str(brave_strings[string_name]) + '" for "' + string_name + '" not found')
                        else:
                            sys.exit('String name "' + string_name + '" not found in base strings')
                    if strings_file_was_changed:
                        strings_tree.write(xml_file_name, encoding="utf-8", xml_declaration=False)
                else:
                    sys.exit('Language xml file not found ' + xml_file_name)
        break
    print('Sync translations to transifex finished successfully')

# sync translations from .xml files, taken from transifex, to .xtb files
# .xtb files should never be edited manually anymore!!!
def SyncTransifexToTranslations():
    # load all strings and calculate their translation id (ignore memory consumption at this point to speed up whole process)
    brave_strings={}
    e = xml.etree.ElementTree.parse(base_strings_file).getroot()
    for string_tag in e.findall('string'):
        string_name = string_tag.get('name')
        string_value = GetFullTextFromTag(string_tag, True)
        if not string_name:
            sys.exit('String name is empty')
        if not string_value:
            sys.exit("String value is empty")
        # calculate translation id
        string_fp = GenerateMessageId(string_value, None)
        if string_name in brave_strings:
            sys.exit('String name "' + string_name + '" is duplicated')
        brave_strings[string_name] = string_fp

    # go through all .xtb files in translations_folder
    replacingNumber = 1
    addingNumber = 1
    for (dirpath, dirnames, filenames) in walk(translations_folder):
        for filename in filenames:
            if filename.endswith('.xtb'):
                translations_tree = xml.etree.ElementTree.parse(translations_folder + '/' + filename)
                translations = translations_tree.getroot()
                # get language id
                lang_id = translations.get('lang').replace('-', '_')
                if not lang_id:
                    sys.exit('Language id not found for ' + filename)
                # if not lang_id == 'uk':
                #   continue
                #there are some differences in language codes, so correct them
                if lang_id == 'ca':
                    lang_id = 'ca_ES'
                elif lang_id == 'bg':
                    lang_id = 'bg_BG'
                elif lang_id == 'iw':
                    lang_id = 'he'
                elif lang_id == 'cs':
                    lang_id = 'cs_CZ'
                elif lang_id == 'ml' or lang_id == 'mr' or lang_id == 'ms' or lang_id == 'bn' or lang_id == 'te' or lang_id == 'ta' or lang_id == 'et' or lang_id == 'kn' or lang_id == 'gu':
                    print('Skipping language "' + lang_id)
                    continue
                print('Processing language "' + lang_id + '"...')
                # find appropriate xml file in transifex folder
                xml_file_name = transifex_folder + '/stringsxml_' + lang_id + '.xml'
                if os.path.isfile(xml_file_name):
                    # go through all strings in a file name
                    strings = xml.etree.ElementTree.parse(xml_file_name).getroot()
                    translations_file_was_changed = False
                    for string_tag in strings.findall('string'):
                        string_name = string_tag.get('name')
                        string_value = CleanUpString(GetFullTextFromTag(string_tag, False))
                        if string_name in brave_strings:
                            # we have its translation id, lets look for it in .xtb file
                            translation_id_found = False
                            for translation_tag in translations.findall('translation'):
                                translation_id = translation_tag.get('id')
                                translation_text = GetFullTextFromTag(translation_tag, False)
                                # we found id, so replace it
                                if translation_id == str(brave_strings[string_name]):
                                    if not translation_text == string_value:
                                        print(str(replacingNumber) + ' replacing "' + translation_text + '" with "' + string_value + '"')
                                        replacingNumber += 1
                                        UpdateTagText(translation_tag, string_tag)
                                        translations_file_was_changed = True
                                    translation_id_found = True
                                    break
                            # could not find translation id, so append it to the end
                            if not translation_id_found:
                                print(str(addingNumber) + ' adding "' + string_name + '" with "' + string_value + '"')
                                addingNumber += 1
                                new_translation_tag = xml.etree.ElementTree.Element('translation')
                                new_translation_tag.set('id', str(brave_strings[string_name]))
                                UpdateTagText(new_translation_tag, string_tag)
                                new_translation_tag.tail = '\n'
                                translations.append(new_translation_tag)
                                translations_file_was_changed = True
                        else:
                            sys.exit('String name "' + string_name + '" not found in base strings')
                    # write changes
                    if translations_file_was_changed:
                        translations_file_name = translations_folder + '/' + filename
                        translations_tree.write(translations_file_name, encoding="utf-8", xml_declaration=False)
                        # we need to add prepend headers
                        f = open(translations_file_name, 'r+')
                        # load all content to the memory to make it faster (size is less than 1Mb, so should not be a problem)
                        content = f.read()
                        f.seek(0, 0)
                        f.write(('<?xml version="1.0" ?>\n<!DOCTYPE translationbundle>\n') + content)
                        f.close()
                else:
                    sys.exit('Language xml file not found ' + xml_file_name)
        break
    print('Sync transifex to translations finished successfully')

# supposedly should be used once and never again (it is already used)
# SyncTranslationsToTransifex()
# main function
SyncTransifexToTranslations()

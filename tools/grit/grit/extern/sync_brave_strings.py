import sys
import xml.etree.ElementTree

# these are hard coded values
base_strings_file='../../../../chrome/android/java/strings/strings.xml'
chrome_strings_file='../../../../chrome/android/java/strings/android_chrome_strings.grd'

# go through the strings in the base file and check for appropriate string in chrome strings file
def CheckStrings():
    base_strings_file_modified = False
    strings_tree = xml.etree.ElementTree.parse(base_strings_file)
    strings = strings_tree.getroot()
    for string_tag in strings.findall('string'):
        string_name = string_tag.get('name')
        string_value = string_tag.text.strip()
        if not string_name:
            sys.exit('String name is empty')
        string_name_ids = 'IDS_' + string_name.upper()    
        messages = xml.etree.ElementTree.parse(chrome_strings_file).getroot().find('release').find('messages')
        message_found = False
        for message_tag in messages.findall('message'):
            message_name = message_tag.get('name')
            message_value = message_tag.text.strip()
            if not message_name:
                sys.exit('Message name is empty')
            if string_name_ids == message_name:
                if not string_value == message_value:
                    string_tag.text = message_value
                    base_strings_file_modified = True
                    print('String "' + string_name + '" updated with value "' + message_value + '"')                
                message_found = True
                break
        if not message_found:
            sys.exit('Message for "' + string_name + '" not found in android_chrome_strings.grd')
    if base_strings_file_modified:
        strings_tree.write(base_strings_file)
    print('Strings check finished successfully')

# check for brave strings in grd file and update or add them (depending on their existance in strings.xml)
def SyncFromGrdToStrings():
    messages = xml.etree.ElementTree.parse(chrome_strings_file).getroot().find('release').find('messages')    
    for message_tag in messages.findall('message'):
        message_name = message_tag.get('name')
        message_value = message_tag.text.strip()
        if not message_name:
            sys.exit('Message name is empty')

# run both functions
SyncFromGrdToStrings()
CheckStrings()

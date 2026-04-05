import zipfile
import xml.etree.ElementTree as ET
import sys

def read_docx(file_path):
    try:
        with zipfile.ZipFile(file_path, 'r') as zf:
            xml_content = zf.read('word/document.xml')
            tree = ET.fromstring(xml_content)
            
            namespaces = {'w': 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'}
            
            paragraphs = tree.findall('.//w:p', namespaces)
            doc_text = []
            for para in paragraphs:
                texts = para.findall('.//w:t', namespaces)
                para_text = ''.join([node.text for node in texts if node.text])
                if para_text:
                    doc_text.append(para_text)
                
            return '\n'.join(doc_text)
    except Exception as e:
        return str(e)

if __name__ == '__main__':
    with open('project_plan.txt', 'w', encoding='utf-8') as f:
        f.write(read_docx(sys.argv[1]))

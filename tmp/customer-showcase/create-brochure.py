from pathlib import Path
import shutil
import fitz
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor, Color, white
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from reportlab.graphics.barcode import code128, qr
from reportlab.graphics.shapes import Drawing
from reportlab.graphics import renderPDF

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output' / 'customer-showcase'
PDF_OUT = ROOT / 'output' / 'pdf'
PREVIEW = ROOT / 'tmp' / 'pdfs' / 'customer-showcase'
for folder in [OUT, PDF_OUT, PREVIEW]:
    folder.mkdir(parents=True, exist_ok=True)
for name, filename in [('Segoe', 'segoeui.ttf'), ('SegoeBold', 'segoeuib.ttf')]:
    pdfmetrics.registerFont(TTFont(name, str(Path('C:/Windows/Fonts') / filename)))

NAVY = HexColor('#102B3F')
TEAL = HexColor('#127760')
PALE = HexColor('#EDF6F2')
INK = HexColor('#173B4D')
MUTED = HexColor('#526F7E')
LINE = HexColor('#D8E5E8')
W, H = A4
path = PDF_OUT / 'Pahal-Retail-Product-Overview.pdf'
c = canvas.Canvas(str(path), pagesize=A4)
c.setTitle('Pahal Retail | Billing and Store Management')
c.setAuthor('Pahal Retail')
c.setSubject('Customer product overview with fictional sample figures')


def text(x, y, value, size=11, color=INK, bold=False):
    c.setFillColor(color)
    c.setFont('SegoeBold' if bold else 'Segoe', size)
    c.drawString(x, y, value)


def paragraph(x, top, width, value, size=11, color=MUTED, leading=None, bold=False):
    style = ParagraphStyle('body', fontName='SegoeBold' if bold else 'Segoe',
                           fontSize=size, leading=leading or size * 1.45, textColor=color)
    p = Paragraph(value, style)
    _, height = p.wrap(width, H)
    p.drawOn(c, x, top - height)
    return height


def box(x, y, width, height, fill=white, stroke=None, radius=10):
    c.setFillColor(fill)
    c.setStrokeColor(stroke or fill)
    c.roundRect(x, y, width, height, radius, fill=1, stroke=int(stroke is not None))


def line(x, y, width, color=LINE):
    c.setStrokeColor(color)
    c.setLineWidth(.65)
    c.line(x, y, x + width, y)


def footer(page):
    line(42, 35, W - 84)
    text(42, 20, 'PAHAL RETAIL  /  Customer overview', 8, MUTED)
    c.setFillColor(MUTED)
    c.setFont('Segoe', 8)
    c.drawRightString(W - 42, 20, f'{page} / 2')


# PAGE 1: a clear proposition, a sample working day and an illustrative receipt.
c.setFillColor(NAVY)
c.rect(0, 547, W, H - 547, fill=1, stroke=0)
text(42, 799, 'PAHAL RETAIL', 18, white, True)
text(42, 779, 'BILLING & STORE MANAGEMENT', 8.8, HexColor('#AFCCD6'))
text(42, 722, 'Your store.', 35, white, True)
text(42, 678, 'A clearer daily view.', 35, white, True)
paragraph(42, 646, 475,
          'Bring billing, stock, purchases and pending balances into one application. '
          'Keep your store records locally on your Windows computer.', 12,
          HexColor('#D4E5EB'), 18)
text(42, 568, 'EXPLORE THE SCREENS. SEE HOW YOUR STORE COULD WORK.', 9,
     HexColor('#AFE7D2'), True)

text(42, 520, 'A sample store day', 12, INK, True)
text(358, 521, 'Fictional figures for demonstration', 8.5, MUTED)
metrics = [('Rs 6,600', 'Sales'), ('36', 'Bills'), ('Rs 550', 'Customer dues'), ('2', 'Low-stock items')]
for i, (value, label) in enumerate(metrics):
    x = 42 + i * 130
    box(x, 430, 121, 72, PALE)
    text(x + 13, 467, value, 19, TEAL, True)
    text(x + 13, 447, label, 9, MUTED)

text(42, 394, 'From checkout to the daily review', 21, INK, True)

rx, ry, rw, rh = 42, 112, 229, 257
box(rx, ry, rw, rh, white, LINE)
text(rx + 15, 349, 'ILLUSTRATIVE BILL', 8, MUTED, True)
text(rx + 15, 325, 'Sample Stationery Store', 13, INK, True)
text(rx + 15, 308, 'Customer: Sample Customer A', 8.5)
line(rx + 15, 294, rw - 30)
text(rx + 15, 280, 'ITEM', 8, MUTED, True)
text(rx + 133, 280, 'QTY', 8, MUTED, True)
text(rx + 172, 280, 'AMOUNT', 8, MUTED, True)
for i, (name, quantity, amount) in enumerate([
        ('A5 Notebook', '2', '130.00'), ('Ball Pen - Blue', '4', '60.00'),
        ('Pencil Set', '1', '40.00'), ('Document Folder', '2', '70.00')]):
    y = 260 - 18 * i
    text(rx + 15, y, name, 8.5)
    text(rx + 142, y, quantity, 8.5)
    c.setFont('Segoe', 8.5)
    c.drawRightString(rx + rw - 15, y, amount)
line(rx + 15, 194, rw - 30)
text(rx + 15, 177, 'TOTAL', 10, INK, True)
text(rx + 151, 177, 'Rs 300.00', 10, INK, True)
text(rx + 15, 162, 'Cash received: Rs 250.00', 8.5)
text(rx + 15, 147, 'Balance due: Rs 50.00', 8.5)
barcode = code128.Code128('INV-00000101', barHeight=13, barWidth=.54,
                          quiet=True, humanReadable=False)
barcode.drawOn(c, rx + 16, 123)
text(rx + 153, 127, 'INV-00000101', 7, MUTED)

for y, title, body in [
    (357, 'Bill with the details you need',
     'Scan products, hold a bill, select a payment method and create a personalised receipt.'),
    (276, 'Keep pending balances visible',
     'Review customer dues and supplier balances alongside everyday store activity.'),
    (195, 'See product profit clearly',
     'Review estimated gross profit by product and date, with missing-cost coverage shown.')]:
    text(294, y, title, 12.5, INK, True)
    paragraph(294, y - 10, 258, body, 10.5, leading=15)

paragraph(42, 88, W - 84,
          'The companion HTML tour displays existing application screens with sample records. '
          'Gross profit excludes rent, wages and other business expenses.', 8.5, leading=12)
footer(1)
c.showPage()

# PAGE 2: six modules matching the offline tour, followed by an invitation to demo.
text(42, 799, 'PAHAL RETAIL', 15, INK, True)
text(42, 778, 'A PRACTICAL TOUR OF YOUR DAILY WORK', 8.5, TEAL, True)
text(42, 736, 'Explore what your team can do.', 26, INK, True)
paragraph(42, 716, W - 84,
          'Open the customer showcase and move through six application screens. '
          'Try searches and filters using fictional store records.', 11, leading=16)

cards = [
    ('01', 'Dashboard', 'Sales, payment methods, customer dues and low-stock alerts in one daily view.',
     'Sales overview  /  Quick actions'),
    ('02', 'New Bill', 'Product and barcode search, quantities, discounts and multiple payment methods.',
     'Hold bills  /  Receipt details'),
    ('03', 'Stock', 'Stock balances, adjustments with reasons, movement history and opening-stock import.',
     'Stock review  /  Recorded changes'),
    ('04', 'Customer Dues', 'Find outstanding bills and follow up using customer details and pending amounts.',
     'Search dues  /  Record collections'),
    ('05', 'Purchases', 'Supplier purchases, amounts paid, purchase returns and supplier statements.',
     'Stock inward  /  Supplier balances'),
    ('06', 'Profit Reports', 'Estimated gross profit by product and date, with recorded-cost coverage visible.',
     'Product ranking  /  Date ranges'),
]
for i, (number, title, body, caption) in enumerate(cards):
    x = 42 + (i % 2) * 263
    y = 532 - (i // 2) * 132
    box(x, y, 248, 119, HexColor('#F5F8F9'), LINE)
    text(x + 15, y + 94, number, 10, TEAL, True)
    text(x + 43, y + 93, title, 14, INK, True)
    paragraph(x + 15, y + 77, 218, body, 10.1, leading=14.4)
    text(x + 15, y + 15, caption, 8.5, TEAL)

box(42, 159, W - 84, 87, PALE)
text(57, 224, 'Local operation. A plan that fits your store.', 13, TEAL, True)
paragraph(57, 212, W - 114,
          'Choose Basic, Plus, Pro or Plus Pro according to the modules and limits you need. '
          'Request a guided setup and a demonstration of your store workflow.', 10.3, leading=14.5)
text(57, 174, 'Module availability follows the licensed plan. Installation requires PostgreSQL.', 8.4, MUTED)

box(42, 51, W - 84, 92, NAVY)
text(57, 121, 'Book a demonstration', 17, white, True)
text(57, 98, 'WhatsApp: +91 96093 33137', 11, HexColor('#C4EFDE'), True)
text(57, 78, 'meankitsatnalika@gmail.com', 10.5, white)
c.linkURL('https://wa.me/919609333137', (57, 94, 340, 111), relative=0)
c.linkURL('mailto:meankitsatnalika@gmail.com', (57, 74, 340, 89), relative=0)
q = qr.QrCodeWidget('https://wa.me/919609333137')
x1, y1, x2, y2 = q.getBounds()
size = 69
d = Drawing(size, size, transform=[size / (x2 - x1), 0, 0, size / (y2 - y1), 0, 0])
d.add(q)
box(W - 137, 64, 73, 73, white, radius=4)
renderPDF.draw(d, c, W - 135, 66)
footer(2)
c.save()
shutil.copy2(path, OUT / path.name)

# Visual QA artifacts: rendered directly from the final PDF.
document = fitz.open(path)
for i, page in enumerate(document):
    page.get_pixmap(matrix=fitz.Matrix(1.5, 1.5), alpha=False).save(PREVIEW / f'page-{i + 1}.png')
print(f'Created {path} ({len(document)} pages)')

from pathlib import Path
import json
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]
html=(root/'docs/LiderTv_Onizleme.html').read_text(encoding='utf-8')
results=[]
def check(name,ok):
    results.append({'test':name,'pass':bool(ok)})
    print(('PASS ' if ok else 'FAIL ')+name)
def swipe(page,cdp,start,end,hold=0):
    point=lambda x,y:[{'x':x,'y':y,'id':0,'radiusX':2,'radiusY':2,'force':1}]
    cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':point(*start)})
    if hold:page.wait_for_timeout(hold)
    for i in range(1,13):
        x=start[0]+(end[0]-start[0])*i/12;y=start[1]+(end[1]-start[1])*i/12
        cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':point(x,y)})
        page.wait_for_timeout(12)
    cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
    page.wait_for_timeout(250)
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox'])
    ctx=browser.new_context(viewport={'width':412,'height':915},has_touch=True,is_mobile=True)
    def fresh():
        page=ctx.new_page();page.set_content(html);page.wait_for_selector('.card');return page,ctx.new_cdp_session(page)
    page,cdp=fresh()
    swipe(page,cdp,(320,450),(100,450))
    check('Actual touch center swipe moves Live to Movies',page.locator('h1').inner_text()=='Filmler')
    page.close()
    page,cdp=fresh()
    swipe(page,cdp,(8,400),(215,400))
    check('Actual edge touch opens category drawer',page.locator('.drawer').count()==1)
    swipe(page,cdp,(270,115),(70,115))
    check('Actual left touch closes category drawer',page.locator('.drawer').count()==0)
    page.close()
    page,cdp=fresh()
    swipe(page,cdp,(200,420),(202,540))
    check('Actual pull down opens scoped search',page.locator('#searchInput').count()==1)
    page.close()
    page,cdp=fresh();page.locator('.navitem[data-id="movies"]').click();page.wait_for_timeout(100)
    box=page.locator('.card').first.bounding_box();pt=(box['x']+box['width']/2,box['y']+80)
    swipe(page,cdp,pt,pt,730)
    check('Actual long poster touch opens simulated player directly',page.locator('.demo-player').count()==1 and page.locator('.details').count()==0)
    page.close()
    page,cdp=fresh()
    first=page.locator('.navitem[data-id="home"]').bounding_box();target=page.locator('.navitem[data-id="movies"]').bounding_box()
    start=(first['x']+first['width']/2,first['y']+first['height']/2);end=(target['x']+target['width']/2,target['y']+target['height']/2)
    swipe(page,cdp,start,end,680)
    boot=page.evaluate("Demo.request('boot',{})")
    check('Actual long touch drag persists menu order',boot['settings']['nav'].index('home')>0)
    page.close()
    page,cdp=fresh();page.locator('.navitem[data-id="movies"]').click();page.locator('.card').first.focus();page.keyboard.down('Enter');page.wait_for_timeout(720);page.keyboard.up('Enter');page.wait_for_timeout(200)
    check('Remote long OK opens simulated player',page.locator('.demo-player').count()==1)
    page.close();browser.close()
(root/'qa/gesture-tests.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
if not all(x['pass'] for x in results):raise SystemExit(1)

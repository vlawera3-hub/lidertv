"""Synthetic desktop SQLite checks of SQL extracted from Store.java.
NOT the user's playlist, NOT an Android performance or encryption test.
"""
from pathlib import Path
import sqlite3,re,json,time
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/tr/lidertv/app/Store.java').read_text()
create=source.split('@Override public void onUpgrade')[0]
sql=re.findall(r'db\.execSQL\("([^"]+)"\);',create)
db=sqlite3.connect(':memory:');results=[]
def check(name,ok):
    results.append({'test':name,'pass':bool(ok)})
    print(('PASS ' if ok else 'FAIL ')+name)
    if not ok:raise AssertionError(name)
for statement in sql:db.execute(statement)
check('Actual Store schema and FTS4 triggers create successfully',len(sql)==13)
db.execute("INSERT INTO sources(id,name,type,secret,gen)VALUES('s','Synthetic','m3u','none',1)")
db.execute("INSERT INTO groups(id,sid,kind,name)VALUES('g','s','movies','Test')")
insert="INSERT INTO items(id,sid,gen,kind,gid,title,title_key,cast_text,cast_key,plot,poster,url,remote_id,extra,added,seen,seq,parent)VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
start=time.monotonic()
def row(i,gen=1):return(str(i),'s',gen,'movies','g','Film '+str(i),'film '+str(i),'Deniz','deniz','','','test','','{}',0,1,i,'')
with db:
    db.executemany(insert,(row(i) for i in range(200000)))
    db.executemany("UPDATE items SET seq=? WHERE id=? AND gen=1",((200000+i,str(i)) for i in range(19906)))
unique=db.execute('SELECT COUNT(*) FROM items').fetchone()[0]
check('219906 synthetic inputs produce 200000 unique rows',unique==200000 and 219906-unique==19906)
check('FTS trigger index has one entry per item',db.execute('SELECT COUNT(*) FROM item_search').fetchone()[0]==200000)
check('Global actor prefix query matches',db.execute("SELECT COUNT(*) FROM item_search WHERE item_search MATCH ?",('(title_key:deniz* OR cast_key:deniz*)',)).fetchone()[0]==200000)
check('Scoped title query excludes cast',db.execute("SELECT COUNT(*) FROM item_search WHERE item_search MATCH ?",('title_key:deniz*',)).fetchone()[0]==0)
frm=re.search(r'private static final String FROM="([^"]+)";',source).group(1)
q='SELECT COUNT(*)'+frm+"WHERE i.parent='' AND g.hidden=0 AND g.locked=0"
check('Current generation visible',db.execute(q).fetchone()[0]==200000)
with db:db.executemany(insert,(row(i,2) for i in range(100)))
check('Incomplete refresh stays hidden; old generation remains',db.execute(q).fetchone()[0]==200000)
db.execute("DELETE FROM items WHERE gen=2")
check('Cancelled refresh leaves old count and FTS intact',db.execute(q).fetchone()[0]==200000 and db.execute('SELECT COUNT(*) FROM item_search').fetchone()[0]==200000)
db.execute("UPDATE groups SET locked=1")
check('Parental group filter hides all items',db.execute(q).fetchone()[0]==0)
db.execute("UPDATE groups SET locked=0")
page=db.execute('SELECT i.id'+frm+"WHERE g.hidden=0 AND g.locked=0 ORDER BY i.seq DESC LIMIT 48").fetchall()
check('Large catalog returns a 48-item page',len(page)==48)
(root/'qa/sql-tests.json').write_text(json.dumps({'scope':'Synthetic desktop SQLite, not Android or user data','synthetic_inputs':219906,'unique':unique,'elapsed_seconds':round(time.monotonic()-start,3),'tests':results},indent=2),encoding='utf-8')

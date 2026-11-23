"""Проверка одинаковых результатов и состояний без сортировки результатов."""
import copy,json,subprocess,sys,tempfile,os
from pathlib import Path
ROOT=(Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[1]).resolve()
JAVA=Path(sys.argv[2]) if len(sys.argv)>2 else Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')
OUT=Path(sys.argv[3]) if len(sys.argv)>3 else ROOT/'work/parity'
sys.path.insert(0,str(ROOT/'src'))
import algorithms,workflow,storage
seed=json.loads((ROOT/'src/data/warehouse.json').read_text(encoding='utf-8'))
cases=[]
def case(name,request): cases.append((name,request))
e=[{'product_id':'P001','expected_quantity':10}]
for name,actual in [('без отклонений',[('P001',10)]),('недостача',[('P001',8)]),('излишек',[('P001',12)]),('незаявленный',[('P001',10),('P002',3)]),('пустой факт',[])]:
    case(name,dict(mode='compare',expected=e,actual=[dict(product_id=p,actual_quantity=q) for p,q in actual]))
case('оба пусты',dict(mode='compare',expected=[],actual=[]))
case('пустое ожидание',dict(mode='compare',expected=[],actual=[dict(product_id='P002',actual_quantity=3)]))
case('порядок результатов',dict(mode='compare',expected=[dict(product_id='P002',expected_quantity=2),*e],actual=[dict(product_id='P003',actual_quantity=1)]))
case('целые без переполнения',dict(mode='compare',expected=[dict(product_id='P001',expected_quantity=10**40)],actual=[dict(product_id='P001',actual_quantity=10**40-1)]))
p=seed['products'][0];cells=seed['storage_cells']
for name,product,qty,cs in [('best fit',p,10,cells),('нет места',p,1000,cells),('пустые ячейки',p,1,[]),('ничья',p,10,[cells[0],dict(cells[0],cell_id='S999')]),('условия',dict(p,storage_conditions='заморозка'),1,cells),('зона',dict(p,allowed_zone='Z'),1,cells),('зона не ограничена',dict(p,allowed_zone=None),10,cells),('точное заполнение',p,20,cells),('блокировка',p,1,[cells[2]])]:
    case(name,dict(mode='select',product=product,quantity=qty,cells=cs))
def flow(name,ops,data=None): case(name,dict(mode='workflow',data=copy.deepcopy(seed if data is None else data),operations=ops))
actual=lambda qty=10:[dict(op='actual',id='D001',date='2026-11-22',items=[dict(product_id='P001',actual_quantity=qty)])]
reg=lambda items=[]:dict(op='discrepancies',id='D001',items=items)
accept=lambda qty=10:dict(op='accept',id='D001',approved=True,details={'I001':dict(batch_number='TEST',expiration_date=None,quantity=qty)})
place=dict(op='place',placement=dict(placement_id='PL002',batch_id='B002',cell_id='S001',quantity=10,placement_date='2026-11-22'))
complete=[*actual(),dict(op='compare_receipt',id='D001'),reg(),accept(),dict(op='select_batch',id='B002'),place]
flow('полный процесс и повторное размещение',[*complete,place,dict(place,placement=dict(place['placement'],placement_id='PL003'))])
for qty,kind in [(8,'недостача'),(12,'излишек')]:
    flow('регистрация '+kind,[*actual(qty),reg([dict(product_id='P001',discrepancy_type=kind,quantity_difference=qty-10)]),accept(8)])
flow('качество и частичная приёмка',[*actual(),reg([dict(product_id='P001',discrepancy_type='качество',quantity_difference=0,description='Повреждена упаковка')]),accept(7)])
flow('неверное качество',[*actual(),reg([dict(product_id='P001',discrepancy_type='качество',quantity_difference=1,description='Брак')])])
flow('повтор факта',[*actual(),*actual()])
flow('отказ согласования',[*actual(),reg(),dict(accept(),approved=False)])
flow('неверные количественные расхождения',[*actual(8),reg()])
flow('новая поставка',[dict(op='expected',client_id='C001',date='2026-11-23',items=e)])
flow('незаявленный товар в процессе',[dict(op='actual',id='D001',date='2026-11-22',items=[dict(product_id='P001',actual_quantity=10),dict(product_id='P002',actual_quantity=3)]),reg([dict(product_id='P002',discrepancy_type='неожиданный товар',quantity_difference=3)]),dict(op='accept',id='D001',approved=True,details={'I001':dict(batch_number='A',expiration_date=None,quantity=10),'I008':dict(batch_number='B',expiration_date=None,quantity=3)}),place,dict(op='place',placement=dict(placement_id='PL003',batch_id='B003',cell_id='S002',quantity=3,placement_date='2026-11-22'))])
for name,mutate in [
 ('битая ссылка',lambda d:d['delivery_items'][0].update(product_id='MISSING')),
 ('неверная вместимость',lambda d:d['storage_cells'][0].update(available_capacity=11)),
 ('неверная разность',lambda d:d['discrepancies'][0].update(quantity_difference=-2)),
 ('неверный знак',lambda d:d['discrepancies'][0].update(quantity_difference=1)),
 ('лишнее поле',lambda d:d['batches'][0].update(product_id='P001')),
 ('нецелое количество',lambda d:d['delivery_items'][0].update(expected_quantity=1.5)),
 ('булево количество',lambda d:d['delivery_items'][0].update(expected_quantity=True)),
 ('null вместо ключа',lambda d:d['products'][0].update(product_id=None)),
 ('неверная дата',lambda d:d['deliveries'][0].update(expected_date='2026-02-30')),
 ('пропущено расхождение',lambda d:d['discrepancies'].clear()),
 ('дубликат ключа',lambda d:d['clients'].append(d['clients'][0].copy()))]:
    d=copy.deepcopy(seed);mutate(d);case(name,dict(mode='validate',data=d))
case('валидная база',dict(mode='validate',data=seed))
case('чтение и запись',dict(mode='io',data=seed))
def operation(data,op):
    name=op['op']
    if name=='expected':return workflow.register_expected_delivery(data,dict(client_id=op['client_id'],expected_date=op['date'],items=op['items']))
    if name=='actual':return workflow.record_actual_receipt(data,op['id'],op['items'],op['date'])
    if name=='compare_receipt':return workflow.receipt_discrepancies(data,op['id'])
    if name=='discrepancies':return workflow.register_discrepancies(data,op['id'],op['items'])
    if name=='accept':return workflow.accept_delivery(data,op['id'],op['details'],op['approved'])
    if name=='select_batch':
        b=storage.find_record(data,'batches','batch_id',op['id']);i=storage.find_record(data,'delivery_items','delivery_item_id',b['delivery_item_id']);p=storage.find_record(data,'products','product_id',i['product_id'])
        return algorithms.select_storage_cell(p,workflow.batch_remaining(data,op['id']),data['storage_cells'])
    if name=='place':return workflow.register_placement(data,op['placement'],op.get('zone'))
def execute(r):
    if r['mode']=='compare':return algorithms.compare_delivery_items(r['expected'],r['actual'])
    if r['mode']=='select':return algorithms.select_storage_cell(r['product'],r['quantity'],r['cells'])
    d=copy.deepcopy(r['data'])
    try:storage.validate_data(d)
    except (ValueError,KeyError,TypeError):return dict(valid=False)
    if r['mode']=='validate':return dict(valid=True)
    if r['mode']=='io':
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/'warehouse.json';storage.save_data(d,path);return storage.load_data(path)
    results=[]
    for op in r['operations']:
        candidate=copy.deepcopy(d)
        try:
            result=copy.deepcopy(operation(candidate,op));storage.validate_data(candidate);d=candidate;results.append(dict(ok=True,result=result))
        except (ValueError,KeyError,TypeError):results.append(dict(ok=False))
    return dict(results=results,state=d)
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'parity-input.json').write_text(json.dumps([r for n,r in cases],ensure_ascii=False),encoding='utf-8')
cp=str(ROOT/'src/java/build/classes')+os.pathsep+str(ROOT/'src/java/lib/gson-2.13.2.jar')
subprocess.run([str(JAVA),'-cp',cp,'ru.mirea.warehouse.VerificationMain',str(OUT/'parity-input.json'),str(OUT/'parity-java.json')],check=True)
java=json.loads((OUT/'parity-java.json').read_text(encoding='utf-8'))
python=[execute(r) for n,r in cases]
def same(a,b):
    if type(a) is not type(b): return False
    if isinstance(a,dict): return a.keys()==b.keys() and all(same(a[k],b[k]) for k in a)
    if isinstance(a,list): return len(a)==len(b) and all(same(x,y) for x,y in zip(a,b))
    return a==b
report=[]
for (name,r),a,b in zip(cases,python,java):
    ok=same(a,b);report.append(dict(case=name,passed=ok));print(('PASS ' if ok else 'FAIL ')+name)
    if not ok:print('Python:',json.dumps(a,ensure_ascii=False));print('Java:',json.dumps(b,ensure_ascii=False))
(OUT/'parity-python.json').write_text(json.dumps(python,ensure_ascii=False,indent=2),encoding='utf-8')
(OUT/'parity-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
assert all(x['passed'] for x in report)
print(f'{len(report)}/{len(report)} совпали')

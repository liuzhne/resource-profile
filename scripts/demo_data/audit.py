"""Readonly production demo audit; export only metadata, counts and inequalities."""
import argparse,datetime,json,os,pathlib,re,subprocess
os.umask(0o077)
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output-dir',type=pathlib.Path,required=True)
args=parser.parse_args()
OUT=args.output_dir;OUT.mkdir(parents=True,exist_ok=True,mode=0o700)
def mysql(sql):
 p=subprocess.run(['k3s','kubectl','-n','educare','exec','-i','deployment/mysql','--','sh','-c',
   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --default-character-set=utf8mb4 --batch --raw --skip-column-names edu_portrait'],input=sql,text=True,capture_output=True,timeout=90)
 if p.returncode:raise RuntimeError('readonly_mysql_query_failed')
 return p.stdout.strip().splitlines()
def scalar(sql):return int(mysql(sql)[0])
names=mysql("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME;")
assert all(re.fullmatch(r'[a-z_]+',n) for n in names)
metadata=[]
for line in mysql("SELECT JSON_OBJECT('table',TABLE_NAME,'column',COLUMN_NAME,'type',COLUMN_TYPE,'nullable',IS_NULLABLE,'extra',EXTRA) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() ORDER BY TABLE_NAME,ORDINAL_POSITION;"):
 metadata.append(json.loads(line))
report={'checked_at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'table_count':len(names),
 'counts':{n:scalar('SELECT COUNT(*) FROM `'+n+'`;') for n in names},
 'max_ids':{n:scalar('SELECT COALESCE(MAX(id),0) FROM `'+n+'`;') for n in names if any(c['table']==n and c['column']=='id' for c in metadata)},
 'columns':metadata,
 'protected_seed_accounts':[json.loads(x) for x in mysql("SELECT JSON_OBJECT('id',id,'username',username,'user_type',user_type,'status',status,'deleted',deleted) FROM sys_user WHERE username IN ('admin','teacher','student') ORDER BY id;")],
 'role_catalog':[json.loads(x) for x in mysql("SELECT JSON_OBJECT('id',id,'code',code,'status',status,'deleted',deleted) FROM sys_role ORDER BY id;")],
 'checks':{},'group_counts':{}}
checks={
 'shared_active_student_bindings':"SELECT COUNT(*) FROM (SELECT user_id FROM student_info WHERE deleted=0 GROUP BY user_id HAVING COUNT(*)>1) x;",
 'placeholder_student_names':"SELECT COUNT(*) FROM student_info WHERE deleted=0 AND (name REGEXP '(^[Xx某]+|(老师|同学)$)' OR name LIKE '%测试%' OR name LIKE '%示例%');",
 'placeholder_teacher_names':"SELECT COUNT(*) FROM teacher_info WHERE deleted=0 AND (name REGEXP '(^[Xx某]+|(老师|同学)$)' OR name LIKE '%测试%' OR name LIKE '%示例%');",
 'student_unknown_birthdate':"SELECT COUNT(*) FROM student_info WHERE deleted=0 AND birth_date IS NULL;",
 'student_unknown_enrollment':"SELECT COUNT(*) FROM student_info WHERE deleted=0 AND enrollment_date IS NULL;",
 'invalid_gpa':"SELECT COUNT(*) FROM student_info WHERE deleted=0 AND (gpa<0 OR gpa>4);",
 'invalid_scores':"SELECT COUNT(*) FROM student_academic_record WHERE deleted=0 AND (score<0 OR score>100 OR credit<=0 OR grade_point<0 OR grade_point>4);",
 'unlinked_assessments':"SELECT COUNT(*) FROM mental_assessment WHERE response_id IS NULL;",
 'unreal_question_count':"SELECT COUNT(*) FROM mental_questionnaire q WHERE q.deleted=0 AND q.questions<>(SELECT COUNT(*) FROM mental_question mq WHERE mq.questionnaire_id=q.id AND mq.deleted=0);",
 'active_agent_tasks':"SELECT COUNT(*) FROM agent_task WHERE deleted=0 AND status NOT IN ('COMPLETED','REJECTED','FAILED');",
 'active_exports':"SELECT COUNT(*) FROM agent_export_task WHERE deleted=0 AND status NOT IN ('DONE','FAILED');",
 'audited_tasks':"SELECT COUNT(DISTINCT task_id) FROM audit_log WHERE task_id IS NOT NULL;"
}
for k,q in checks.items():report['checks'][k]=scalar(q)
for key,q in {
 'student_grades':"SELECT JSON_OBJECT('value',grade,'count',COUNT(*)) FROM student_info WHERE deleted=0 GROUP BY grade;",
 'teacher_titles':"SELECT JSON_OBJECT('value',title,'count',COUNT(*)) FROM teacher_info WHERE deleted=0 GROUP BY title;",
 'assessment_levels':"SELECT JSON_OBJECT('value',level,'count',COUNT(*)) FROM mental_assessment GROUP BY level;",
 'attendance_status':"SELECT JSON_OBJECT('value',status,'count',COUNT(*)) FROM student_attendance WHERE deleted=0 GROUP BY status;",
}.items():report['group_counts'][key]=[json.loads(x) for x in mysql(q)]
(OUT/'audit-before.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'table_count':report['table_count'],'counts':report['counts'],'checks':report['checks'],'protected_account_count':len(report['protected_seed_accounts'])},ensure_ascii=False))
